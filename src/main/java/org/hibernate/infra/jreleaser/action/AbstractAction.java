package org.hibernate.infra.jreleaser.action;

import java.util.Map;

import org.jreleaser.extensions.api.workflow.WorkflowAdapter;
import org.jreleaser.model.Active;
import org.jreleaser.model.api.JReleaserContext;
import org.jreleaser.model.api.hooks.ExecutionEvent;

abstract class AbstractAction extends WorkflowAdapter {

	private Active active;

	@Override
	public final void init(JReleaserContext context, Map<String, Object> properties) {
		active = Active.of( properties.getOrDefault( "active", "NEVER" ).toString() );
		if ( isEnabled(context) ) {
			initAction( context, properties );
		}
	}

	protected abstract void initAction(JReleaserContext context, Map<String, Object> properties);

	@Override
	public void onWorkflowStep(ExecutionEvent event, JReleaserContext context) {
		if ( ExecutionEvent.Type.BEFORE.equals( event.getType() ) && eventName().equals( event.getName() ) ) {
			if ( isEnabled( context ) ) {
				context.getLogger().info( "Executing {}: {}", name(), event );
				action( event, context );
			}
			else {
				context.getLogger().info( "Skipping {} action because it is disabled", name() );
			}
		}
	}

	protected String getProperty(String property, Map<String, Object> properties) {
		Object value = properties.get( property );
		if ( value == null ) {
			throw new IllegalArgumentException( "property " + property + " is null" );
		}
		return value.toString();
	}

	protected String getProperty(String property, String defaultValue, Map<String, Object> properties) {
		Object value = properties.get( property );
		if ( value == null ) {
			return defaultValue;
		}
		return value.toString();
	}

	protected abstract void action(ExecutionEvent event, JReleaserContext context);

	protected abstract String eventName();

	public boolean isEnabled(JReleaserContext context) {
		return active.check( context.getModel().getProject() );
	}

	protected String name() {
		return this.getClass().getSimpleName();
	}
}
