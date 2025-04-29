package org.hibernate.infra.jreleaser.action;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.jreleaser.model.api.JReleaserContext;
import org.jreleaser.model.api.hooks.ExecutionEvent;
import org.jreleaser.version.SemanticVersion;

public class MergeMavenMetadataAction extends AbstractAction {

	enum Mode {
		MERGE,
		RECREATE;
	}

	private static final Pattern VERSION_PATTERN = Pattern.compile( "\\d++\\.\\d++\\.\\d++.*+" );
	private static final DateTimeFormatter LAST_UPDATED_FORMAT = DateTimeFormatter.ofPattern( "yyyyMMddHHmmss" );

	private Path stagingRepository;
	private String projectVersion;
	private String repositoryUrl;
	private String repositoryServiceUrl;
	private Duration retryInterval;
	private Mode mode;

	@Override
	public void initAction(JReleaserContext context, Map<String, Object> properties) {
		projectVersion = context.props().get( "projectVersion" ).toString();
		mode = Mode.valueOf( getProperty( "recreateMetadata", "MERGE", properties ) );

		if ( context.getModel().getProject().isRelease() ) {
			switch ( mode ) {
				case RECREATE -> repositoryServiceUrl = getUrlProperty( "releaseServiceUrl", properties );
				case MERGE -> repositoryUrl = getUrlProperty( "releaseUrl", properties );
				default -> throw new IllegalStateException();
			}
		}
		else {
			switch ( mode ) {
				case RECREATE -> repositoryServiceUrl = getUrlProperty( "snapshotServiceUrl", properties );
				case MERGE -> repositoryUrl = getUrlProperty( "snapshotUrl", properties );
				default -> throw new IllegalStateException();
			}
		}

		retryInterval = Duration.parse( getProperty( "retryInterval", "PT30.0S", properties ) );

		String stagingRepositoryString = getProperty( "stagingRepository", properties );
		stagingRepository = Paths.get( stagingRepositoryString );
		if ( Files.notExists( stagingRepository ) ) {
			context.getLogger().error( "Cannot find staging repository at: {}", stagingRepositoryString );
		}
	}

	private String getUrlProperty(String property, Map<String, Object> properties) {
		var url = getProperty( property, properties );
		if ( !url.endsWith( "/" ) ) {
			url += "/";
		}
		return url;
	}

	@Override
	public void action(ExecutionEvent event, JReleaserContext context) {
		Consumer<Path> mergeAction;
		Consumer<Path> removeAction;

		if ( context.isDryrun() ) {
			mergeAction = path -> {
				try ( var sw = new StringWriter(); ) {
					processXml( context, path, sw );
					context.getLogger().info( "Would merge " + path + "with the following resulting XML:\n" + sw );
				}
				catch (IOException e) {
					throw new RuntimeException( e );
				}
			};
			removeAction = path -> context.getLogger().info( "Would remove " + path );
		}
		else {
			mergeAction = path -> {
				try ( FileWriter fw = new FileWriter( path.toAbsolutePath().toFile(), false ) ) {
					processXml( context, path, fw );
				}
				catch (IOException e) {
					throw new RuntimeException( e );
				}
			};
			removeAction = path -> {
				try {
					Files.delete( path );
				}
				catch (IOException e) {
					throw new RuntimeException( e );
				}
			};
		}

		try {
			context.getLogger().debug( "About to walk the " + stagingRepository + " repository" );
			Files.walkFileTree(
					stagingRepository, new SimpleFileVisitor<>() {
						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
							Path parent = file.getParent();
							if ( parent != null && !parent.getFileName().toString().equals( projectVersion ) ) {
								String currentFileName = file.getFileName().toString();
								if ( "maven-metadata.xml".equals( currentFileName ) ) {
									mergeAction.accept( file );
								}
								else if ( currentFileName.startsWith( "maven-metadata.xml" ) ) {
									removeAction.accept( file );
								}
							}
							else {
								context.getLogger().debug( "Skipping maven metadata file that is inside of the version directory: " + file );
							}

							return super.visitFile( file, attrs );
						}
					}
			);
		}
		catch (IOException e) {
			throw new RuntimeException( e );
		}
	}

	private void processXml(JReleaserContext context, Path path, Writer fw) throws MalformedURLException {
		ArtifactCoordinates coordinates = ArtifactCoordinates.from( stagingRepository, path.getParent() );
		// it probably would've been better to read the xml from the stream, but...
		// let's prefetch the entier doc with retries before we pass it to the xml parser:
		if ( Mode.MERGE.equals( mode ) ) {
			// e.g. https://oss.sonatype.org/content/repositories/snapshots/org/hibernate/orm/hibernate-core/maven-metadata.xml
			byte[] xml = downloadWithRetry( context, 5, URI.create( repositoryUrl + coordinates.path + "/maven-metadata.xml" ).toURL() );
			mergeMetadataXml( xml, projectVersion, fw );
		}
		else {
			// e.g. https://oss.sonatype.org/service/local/repositories/snapshots/content/org/hibernate/orm/hibernate-core/
			byte[] xml = downloadWithRetry( context, 5, URI.create( repositoryServiceUrl + coordinates.path + "/" ).toURL() );
			recreateMetadataXml( xml, projectVersion, coordinates.groupId(), coordinates.artifactId(), fw );
		}
	}

	@Override
	protected String eventName() {
		return "checksum";
	}

	private record ArtifactCoordinates(String groupId, String artifactId, String path) {
		static ArtifactCoordinates from(Path staging, Path path) {
			Path relative = staging.relativize( path );
			return new ArtifactCoordinates( path.getFileName().toString(), relative.getParent().toString().replace( File.separatorChar, '.' ), relative.toString() );
		}
	}

	public byte[] downloadWithRetry(JReleaserContext context, int retry, URL url) {
		if ( retry <= 0 ) {
			throw new RuntimeException( "Cannot download Maven Metadata from " + url + " because the retry limit has been reached." );
		}
		context.getLogger().info( "Downloading Maven Metadata from " + url );
		try (
				InputStream is = url.openConnection().getInputStream();
				BufferedInputStream buf = new BufferedInputStream( is )
		) {
			return buf.readAllBytes();
		}
		catch (IOException e) {
			try {
				Thread.sleep( retryInterval );
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new RuntimeException( e );
			}
			retry--;
			context.getLogger().error( "Error downloading Maven Metadata from " + url + ". Will try " + retry + " more times.", e );
			return downloadWithRetry( context, retry, url );
		}
	}

	public static void recreateMetadataXml(byte[] in, String version, String groupId, String artifactId, Writer out) {
		recreateMetadataXml( new ByteArrayInputStream( in ), version, groupId, artifactId, out );
	}

	public static void recreateMetadataXml(InputStream in, String version, String groupId, String artifactId, Writer out) {
		SemanticVersion latest = SemanticVersion.of( version );

		XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
		XMLOutputFactory outFactory = XMLOutputFactory.newInstance();
		XMLEventFactory eventFactory = XMLEventFactory.newInstance();

		try {
			XMLEventReader reader = xmlInputFactory.createXMLEventReader( in );
			XMLEventWriter writer = outFactory.createXMLEventWriter( out );

			writer.add( eventFactory.createStartDocument() );
			writer.add( eventFactory.createCharacters( "\n" ) );
			writer.add( eventFactory.createStartElement( "", "", "metadata" ) );
			writer.add( eventFactory.createCharacters( "\n  " ) );
			writer.add( eventFactory.createStartElement( "", "", "groupId" ) );
			writer.add( eventFactory.createCharacters( groupId ) );
			writer.add( eventFactory.createEndElement( "", "", "groupId" ) );
			writer.add( eventFactory.createCharacters( "\n  " ) );
			writer.add( eventFactory.createStartElement( "", "", "artifactId" ) );
			writer.add( eventFactory.createCharacters( artifactId ) );
			writer.add( eventFactory.createEndElement( "", "", "artifactId" ) );
			writer.add( eventFactory.createCharacters( "\n  " ) );
			writer.add( eventFactory.createStartElement( "", "", "versioning" ) );
			writer.add( eventFactory.createCharacters( "\n    " ) );
			writer.add( eventFactory.createStartElement( "", "", "versions" ) );
			while ( reader.hasNext() ) {
				XMLEvent xmlEvent = reader.nextEvent();
				if ( xmlEvent.isStartElement() && xmlEvent.asStartElement().getName().getLocalPart().equals( "text" ) ) {
					XMLEvent text = reader.nextEvent();
					if ( !text.isCharacters() ) {
						throw new IllegalStateException( "Unexpected evet instead of characters: " + text );
					}
					String data = text.asCharacters().getData();
					if ( VERSION_PATTERN.matcher( data ).matches() ) {
						SemanticVersion curr = SemanticVersion.of( data );
						if ( latest.compareTo( curr ) <= 0 ) {
							latest = curr;
						}
						writer.add( eventFactory.createCharacters( "\n      " ) );
						writer.add( eventFactory.createStartElement( "", "", "version" ) );
						writer.add( eventFactory.createCharacters( data ) );
						writer.add( eventFactory.createEndElement( "", "", "version" ) );
						reader.nextEvent();// just get the value out of the stream (discard)
					}
				}
			}
			writer.add( eventFactory.createCharacters( "\n    " ) );
			writer.add( eventFactory.createEndElement( "", "", "versions" ) );
			writer.add( eventFactory.createCharacters( "\n    " ) );
			writer.add( eventFactory.createStartElement( "", "", "latest" ) );
			writer.add( eventFactory.createCharacters( latest.toString() ) );
			writer.add( eventFactory.createEndElement( "", "", "latest" ) );
			writer.add( eventFactory.createCharacters( "\n    " ) );
			writer.add( eventFactory.createStartElement( "", "", "lastUpdated" ) );
			writer.add( eventFactory.createCharacters( LAST_UPDATED_FORMAT.format( LocalDateTime.now( Clock.systemUTC() ) ) ) );
			writer.add( eventFactory.createEndElement( "", "", "lastUpdated" ) );
			writer.add( eventFactory.createCharacters( "\n  " ) );
			writer.add( eventFactory.createEndElement( "", "", "versioning" ) );
			writer.add( eventFactory.createCharacters( "\n" ) );
			writer.add( eventFactory.createEndElement( "", "", "metadata" ) );
			writer.add( eventFactory.createEndDocument() );
			writer.flush();
			writer.close();
		}
		catch (XMLStreamException e) {
			throw new RuntimeException( e );
		}
	}

	public static void mergeMetadataXml(byte[] in, String version, Writer out) {
		mergeMetadataXml( new ByteArrayInputStream( in ), version, out );
	}

	public static void mergeMetadataXml(InputStream in, String version, Writer out) {
		try {
			XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
			XMLOutputFactory outFactory = XMLOutputFactory.newInstance();
			XMLEventFactory eventFactory = XMLEventFactory.newInstance();

			XMLEventReader reader = xmlInputFactory.createXMLEventReader( in );
			XMLEventWriter writer = outFactory.createXMLEventWriter( out );

			boolean hasCurrentVersion = false;

			while ( reader.hasNext() ) {
				XMLEvent xmlEvent = reader.nextEvent();
				if ( xmlEvent.isStartElement() && xmlEvent.asStartElement().getName().getLocalPart().equals( "lastUpdated" ) ) {
					writer.add( xmlEvent );
					reader.nextEvent();// just get the value out of the stream (discard)
					writer.add( eventFactory.createCharacters( LAST_UPDATED_FORMAT.format( LocalDateTime.now( Clock.systemUTC() ) ) ) );
					writer.add( reader.nextEvent() );
					continue;
				}
				if ( xmlEvent.isStartElement() && xmlEvent.asStartElement().getName().getLocalPart().equals( "version" ) ) {
					writer.add( xmlEvent );
					XMLEvent ver = reader.nextEvent();
					if ( !ver.isCharacters() ) {
						throw new IllegalStateException( "Unexpected event when reading version value: " + ver );
					}
					hasCurrentVersion |= ver.asCharacters().getData().equals( version );
					writer.add( ver );
					writer.add( reader.nextEvent() );

					continue;
				}
				if ( xmlEvent.isStartElement() && xmlEvent.asStartElement().getName().getLocalPart().equals( "latest" ) ) {
					writer.add( xmlEvent );
					XMLEvent ver = reader.nextEvent();
					if ( !ver.isCharacters() ) {
						throw new IllegalStateException( "Unexpected event when reading version value: " + ver );
					}
					writer.add( eventFactory.createCharacters( getLatest( version, ver.asCharacters().getData() ) ) );
					writer.add( reader.nextEvent() );

					continue;
				}
				if ( xmlEvent.isEndElement() && xmlEvent.asEndElement().getName().getLocalPart().equals( "versions" ) && !hasCurrentVersion ) {
					writer.add( eventFactory.createStartElement( "", "", "version" ) );
					writer.add( eventFactory.createCharacters( version ) );
					writer.add( eventFactory.createEndElement( "", "", "version" ) );
				}
				writer.add( xmlEvent );
			}
			writer.flush();
			writer.close();
			reader.close();
		}
		catch (javax.xml.stream.XMLStreamException e) {
			throw new RuntimeException( e );
		}
	}

	private static String getLatest(String current, String latest) {
		if ( SemanticVersion.of( current ).compareTo( SemanticVersion.of( latest ) ) < 0 ) {
			return latest;
		}
		return current;
	}
}
