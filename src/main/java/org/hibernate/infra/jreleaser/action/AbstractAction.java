package org.hibernate.infra.jreleaser.action;

import java.util.Map;

import org.jreleaser.extensions.api.workflow.WorkflowAdapter;
import org.jreleaser.model.api.JReleaserContext;
import org.jreleaser.model.api.hooks.ExecutionEvent;

abstract class AbstractAction extends WorkflowAdapter {

	private boolean enabled;

	@Override
	public final void init(JReleaserContext context, Map<String, Object> properties) {
		enabled = Boolean.parseBoolean( properties.getOrDefault( "enabled", "false" ).toString() );

		initAction( context, properties );
	}

	protected abstract void initAction(JReleaserContext context, Map<String, Object> properties);

	@Override
	public void onWorkflowStep(ExecutionEvent event, JReleaserContext context) {
		if ( ExecutionEvent.Type.BEFORE.equals( event.getType() ) && eventName().equals( event.getName() ) ) {
			if ( isEnabled() ) {
				System.out.printf( "%s: %s%n", name(), event );
				action( event, context );
			}
			else {
				System.out.printf( "Skipping %s action because it is disabled%n", name() );
			}
		}
	}

	protected abstract void action(ExecutionEvent event, JReleaserContext context);

	protected abstract String eventName();

	public boolean isEnabled() {
		return enabled;
	}

	protected String name() {
		return this.getClass().getSimpleName();
	}
}
