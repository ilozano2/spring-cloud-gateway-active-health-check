package io.spring.ilozano.heatlhcheck.active.service.indicator;


import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Simple {@link HealthIndicator} based on a flag {@link #down} for testing purposes.
 */
@Component
public class SwitchHealhCheckIndicator implements HealthIndicator {
	boolean down;

	@Override
	public Health health() {
		return down ? Health.down().build() : Health.up().build();
	}

	public boolean isDown() {
		return down;
	}

	public void setDown(boolean down) {
		this.down = down;
	}
}
