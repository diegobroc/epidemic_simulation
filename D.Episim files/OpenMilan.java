/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.matsim.run.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;

import java.nio.file.Path;

/**
 * Scenario based on the publicly available OpenBerlin scenario (https://github.com/matsim-scenarios/matsim-berlin).
 */
public class OpenMilan extends AbstractSnzScenario2020_Milan {

	public static Path INPUT = EpisimUtils.resolveInputPath("./episim-input");


	public static void addDefaultParams(EpisimConfigGroup config) {

		//Default parameters
		// pt
		config.getOrAddContainerParams("pt", "tr");
		// regular out-of-home acts:
		config.getOrAddContainerParams("work");
		config.getOrAddContainerParams("leisure", "leis");
		config.getOrAddContainerParams("edu");
		config.getOrAddContainerParams("shop");
		config.getOrAddContainerParams("errands");
		config.getOrAddContainerParams("business");
		config.getOrAddContainerParams("other");
		// freight act:
		config.getOrAddContainerParams("freight");
		// home act:
		config.getOrAddContainerParams("home");
		config.getOrAddContainerParams("quarantine_home");
	}

	public static void addCustomParams(EpisimConfigGroup config){
		//Custom parameters
		//Casa
		config.getOrAddContainerParams("casa").setContactIntensity(3.0 / 4.);
		//Attività 'attivi':
		config.getOrAddContainerParams("lavoroEssenziale").setContactIntensity(3.0);
		config.getOrAddContainerParams("lavoroNonEssenziale").setContactIntensity(3.0);
		config.getOrAddContainerParams("studio").setContactIntensity(5.0);
		//Ristoro:
		config.getOrAddContainerParams("ristoro").setContactIntensity(6.0);
		//Spese e servizi:
		config.getOrAddContainerParams("servizi").setContactIntensity(3.0);
		config.getOrAddContainerParams("spesaEssenziale").setContactIntensity(2.0);
		config.getOrAddContainerParams("spesaNonEssenziale").setContactIntensity(2.0);
		//Tempo libero e sport:
		config.getOrAddContainerParams("turismo").setContactIntensity(5.00);
		config.getOrAddContainerParams("sport").setContactIntensity(5.0);
		config.getOrAddContainerParams("tempoLibero").setContactIntensity(5.0);
		//Serata
		config.getOrAddContainerParams("serata").setContactIntensity(6.0);
	}

	@Provides
	@Singleton
	public Config config() {

		Config config = AbstractSnzScenario2020_Milan.getBaseConfig(); //Crea struttura base

		config.facilities().setInputFile(INPUT.resolve("facilities0.25.xml.gz").toString()); //Importo facilities.xml
		config.vehicles().setVehiclesFile(INPUT.resolve("output_vehicles.xml.gz").toString()); //Importa i tipi di veicolo da file
		config.households().setInputFile(INPUT.resolve("output_households.xml.gz").toString()); //Importo households.xml

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile(INPUT.resolve("output_events-0.25.xml.gz").toString()); //Importa eventi da file

		episimConfig.setInitialInfections(5);
		episimConfig.setStartDate("2020-02-16");
		episimConfig.setMaxContacts(3);
		episimConfig.setSampleSize(0.25);

		config.controler().setOutputDirectory("./output-NoLockdown");

		episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config()
				.restrict(episimConfig.getStartDate(),Restriction.of(1),AbstractSnzScenario2020_Milan.DEFAULT_ACTIVITIES)
				.build()
		);

		return config;
	}

}
