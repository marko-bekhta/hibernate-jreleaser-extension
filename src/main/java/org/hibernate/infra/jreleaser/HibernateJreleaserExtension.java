package org.hibernate.infra.jreleaser;

import java.util.Set;

import org.hibernate.infra.jreleaser.action.DocumentationUpload;

import org.jreleaser.extensions.api.Extension;
import org.jreleaser.extensions.api.ExtensionPoint;

public final class HibernateJreleaserExtension implements Extension {
	@Override
	public String getName() {
		// provide a name, used for matching the name in the configuration DSL
		return "hibernate-jreleaser-extension";
	}

	@Override
	public Set<ExtensionPoint> provides() {
		return Set.of( new DocumentationUpload() );
	}
}
