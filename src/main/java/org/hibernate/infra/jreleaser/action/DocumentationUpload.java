package org.hibernate.infra.jreleaser.action;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jreleaser.model.api.JReleaserContext;
import org.jreleaser.model.api.hooks.ExecutionEvent;

public class DocumentationUpload extends AbstractAction {

	private static final Pattern VERSION_FAMILY = Pattern.compile( "^\\d++\\.\\d++" );

	private List<String> command;

	@Override
	public void initAction(JReleaserContext context, Map<String, Object> properties) {
		String projectVersion = context.props().get( "projectVersion" ).toString();
		Matcher matcher = VERSION_FAMILY.matcher( projectVersion );
		if ( !matcher.find() ) {
			throw new IllegalArgumentException( "Invalid project version: " + projectVersion );
		}
		String versionFamily = matcher.group();
		String server = getProperty( "server", properties );
		String sourceDirectory = interpolate( getProperty( "sourceDirectory", properties ), projectVersion, versionFamily );
		String destinationDirectory = interpolate( getProperty( "destinationDirectory", properties ), projectVersion, versionFamily );

		command = List.of(
				"rsync",
				"-rzh",
				"--progress",
				"--delete",
				sourceDirectory,
				"%s:%s".formatted( server, destinationDirectory )
		);
	}

	private String getProperty(String property, Map<String, Object> properties) {
		Object value = properties.get( property );
		if ( value == null ) {
			throw new IllegalArgumentException( "property " + property + " is null" );
		}
		return value.toString();
	}

	private String interpolate(String value, String version, String family) {
		return value.replace( "{{version}}", version ).replace( "{{versionFamily}}", family );
	}

	@Override
	public void action(ExecutionEvent event, JReleaserContext context) {
		if ( context.isDryrun() ) {
			context.getLogger().info( "command to run: " + command );
			return;
		}

		try {
			ProcessBuilder processBuilder = new ProcessBuilder( command );
			processBuilder.inheritIO();
			Process process = processBuilder.start();
			int exitCode = process.waitFor();
			if ( exitCode == 0 ) {
				context.getLogger().info( "Rsync completed successfully" );
			}
			else {
				context.getLogger().error( "Rsync failed with exit code: " + exitCode );
			}
		}
		catch (IOException | InterruptedException e) {
			context.getLogger().error( "Failed to execute command: " + command, e );
		}
	}

	@Override
	protected String eventName() {
		return "upload";
	}
}
