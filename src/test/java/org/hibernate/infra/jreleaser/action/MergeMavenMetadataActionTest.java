package org.hibernate.infra.jreleaser.action;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MergeMavenMetadataActionTest {

	@Test
	void merge() {
		try ( var sw = new StringWriter(); ) {
			var projectVersion = "9.0.0-SNAPSHOT";
			MergeMavenMetadataAction.mergeMetadataXml( MERGE_XML.getBytes( StandardCharsets.UTF_8 ), projectVersion, sw );
			String xml = sw.toString();
			Assertions.assertTrue(
					Pattern.compile( "<\\?xml version=\\\"1\\.0\\\" encoding=\\\"UTF-8\\\"\\?>\\s*<metadata>\\s*" +
							"<groupId>org\\.hibernate\\.orm</groupId>\\s*" +
							"<artifactId>hibernate-core</artifactId>\\s*" +
							"<versioning>\\s*" +
							"<latest>9\\.0\\.0-SNAPSHOT</latest>\\s*" +
							"<versions>\\s*" +
							"<version>7\\.0\\.7-SNAPSHOT</version>\\s*" +
							"<version>9\\.0\\.0-SNAPSHOT</version>\\s*" +
							"</versions>\\s*" +
							"<lastUpdated>\\d{14}</lastUpdated>\\s*" + // the date will change all the time, and no point in dragging a fixed clock into this, just to test things.
							"</versioning>\\s*</metadata>\\s*" ).matcher(
							xml ).matches() );
			var a = """
				<?xml version="1.0" encoding="UTF-8"?>
				<metadata>
				  <groupId>org.hibernate</groupId>
				  <artifactId>hibernate-core</artifactId>
				  <versioning>
				    <latest>7.0.7-SNAPSHOT</latest>
				    <versions>
				      <version>7.0.7-SNAPSHOT</version>
				    </versions>
				    <lastUpdated>20250429074943</lastUpdated>
				  </versioning>
				</metadata>
				
				""";
		}
		catch (IOException e) {
			fail( e );
		}
	}

	@Test
	void recreate() {
		var projectVersion = "9.0.0-SNAPSHOT";
		var groupId = "org.hibernate.orm";
		var artifactId = "hibernate-core";
		try ( var sw = new StringWriter(); ) {
			MergeMavenMetadataAction.recreateMetadataXml( RECREATE_XML.getBytes( StandardCharsets.UTF_8 ), projectVersion, groupId, artifactId, sw );
			String xml = sw.toString();
			Assertions.assertTrue(
					Pattern.compile( "<\\?xml version=\\\"1\\.0\\\" encoding=\\\"UTF-8\\\"\\?>\\s*<metadata>\\s*" +
							"<groupId>org\\.hibernate\\.orm</groupId>\\s*" +
							"<artifactId>hibernate-core</artifactId>\\s*" +
							"<versioning>\\s*<versions>\\s*" +
							"<version>6\\.4\\.0-SNAPSHOT</version>\\s*" +
							"<version>6\\.4\\.9-SNAPSHOT</version>\\s*" +
							"<version>6\\.2\\.13-SNAPSHOT</version>\\s*" +
							"<version>6\\.5\\.0-SNAPSHOT</version>\\s*" +
							"<version>6\\.4\\.7-SNAPSHOT</version>\\s*" +
							"<version>6\\.3\\.3-SNAPSHOT</version>\\s*" +
							"</versions>\\s*" +
							"<latest>9\\.0\\.0-SNAPSHOT</latest>\\s*" +
							"<lastUpdated>\\d{14}</lastUpdated>\\s*" + // the date will change all the time, and no point in dragging a fixed clock into this, just to test things.
							"</versioning>\\s*</metadata>\\s*" ).matcher(
							xml ).matches() );
		}
		catch (IOException e) {
			fail( e );
		}
	}

	private static final String MERGE_XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<metadata>
			  <groupId>org.hibernate.orm</groupId>
			  <artifactId>hibernate-core</artifactId>
			  <versioning>
			    <latest>7.0.7-SNAPSHOT</latest>
			    <versions>
			      <version>7.0.7-SNAPSHOT</version>
			    </versions>
			    <lastUpdated>20250429074943</lastUpdated>
			  </versioning>
			</metadata>
			""";

	private static final String RECREATE_XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<content>
			  <data>
			    <content-item>
			      <resourceURI>https://oss.sonatype.org/service/local/repositories/snapshots/content/org/hibernate/orm/hibernate-core/6.4.0-SNAPSHOT/</resourceURI>
			      <relativePath>/org/hibernate/orm/hibernate-core/6.4.0-SNAPSHOT/</relativePath>
			      <text>6.4.0-SNAPSHOT</text>
			      <leaf>false</leaf>
			      <lastModified>2024-09-30 07:38:24.321 UTC</lastModified>
			      <sizeOnDisk>-1</sizeOnDisk>
			    </content-item>
			    <content-item>
			      <resourceURI>https://oss.sonatype.org/service/local/repositories/snapshots/content/org/hibernate/orm/hibernate-core/maven-metadata.xml.sha512</resourceURI>
			      <relativePath>/org/hibernate/orm/hibernate-core/maven-metadata.xml.sha512</relativePath>
			      <text>maven-metadata.xml.sha512</text>
			      <leaf>true</leaf>
			      <lastModified>2025-04-26 18:38:56.885 UTC</lastModified>
			      <sizeOnDisk>128</sizeOnDisk>
			    </content-item>
			    <content-item>
			      <resourceURI>https://oss.sonatype.org/service/local/repositories/snapshots/content/org/hibernate/orm/hibernate-core/6.4.9-SNAPSHOT/</resourceURI>
			      <relativePath>/org/hibernate/orm/hibernate-core/6.4.9-SNAPSHOT/</relativePath>
			      <text>6.4.9-SNAPSHOT</text>
			      <leaf>false</leaf>
			      <lastModified>2024-09-30 07:38:24.609 UTC</lastModified>
			      <sizeOnDisk>-1</sizeOnDisk>
			    </content-item>
			    <content-item>
			      <resourceURI>https://oss.sonatype.org/service/local/repositories/snapshots/content/org/hibernate/orm/hibernate-core/6.2.13-SNAPSHOT/</resourceURI>
			      <relativePath>/org/hibernate/orm/hibernate-core/6.2.13-SNAPSHOT/</relativePath>
			      <text>6.2.13-SNAPSHOT</text>
			      <leaf>false</leaf>
			      <lastModified>2024-09-30 07:38:24.849 UTC</lastModified>
			      <sizeOnDisk>-1</sizeOnDisk>
			    </content-item>
			    <content-item>
			      <resourceURI>https://oss.sonatype.org/service/local/repositories/snapshots/content/org/hibernate/orm/hibernate-core/6.5.0-SNAPSHOT/</resourceURI>
			      <relativePath>/org/hibernate/orm/hibernate-core/6.5.0-SNAPSHOT/</relativePath>
			      <text>6.5.0-SNAPSHOT</text>
			      <leaf>false</leaf>
			      <lastModified>2024-09-30 07:38:25.413 UTC</lastModified>
			      <sizeOnDisk>-1</sizeOnDisk>
			    </content-item>
			    <content-item>
			      <resourceURI>https://oss.sonatype.org/service/local/repositories/snapshots/content/org/hibernate/orm/hibernate-core/6.4.7-SNAPSHOT/</resourceURI>
			      <relativePath>/org/hibernate/orm/hibernate-core/6.4.7-SNAPSHOT/</relativePath>
			      <text>6.4.7-SNAPSHOT</text>
			      <leaf>false</leaf>
			      <lastModified>2024-09-30 07:38:25.717 UTC</lastModified>
			      <sizeOnDisk>-1</sizeOnDisk>
			    </content-item>
			    <content-item>
			      <resourceURI>https://oss.sonatype.org/service/local/repositories/snapshots/content/org/hibernate/orm/hibernate-core/6.3.3-SNAPSHOT/</resourceURI>
			      <relativePath>/org/hibernate/orm/hibernate-core/6.3.3-SNAPSHOT/</relativePath>
			      <text>6.3.3-SNAPSHOT</text>
			      <leaf>false</leaf>
			      <lastModified>2024-09-30 07:38:43.942 UTC</lastModified>
			      <sizeOnDisk>-1</sizeOnDisk>
			    </content-item>
			  </data>
			</content>
			""";
}
