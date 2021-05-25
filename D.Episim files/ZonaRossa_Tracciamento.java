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

import com.google.inject.Provides;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.Transition;
import org.matsim.episim.model.input.ActivityParticipation;
import org.matsim.episim.model.input.CreateRestrictionsFromCSV;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

/**
 * Snz scenario for Berlin.
 *
 * @see AbstractSnzScenario
 */
public final class ZonaRossa_Tracciamento extends AbstractSnzScenario2020_Milan {

	public static Path INPUT = EpisimUtils.resolveInputPath("./episim-input");

	private static ConfigBuilder basePolicy(ActivityParticipation activityParticipation, Map<String, Double> ciCorrections,
			 											long introductionPeriod, Double maskCompliance, boolean restrictSchoolsAndDayCare,
														boolean restrictUniversities, boolean restrictNoEssentials) throws IOException {

		ConfigBuilder restrictions;

		restrictions = FixedPolicy.config(); //Non ho una policy passata, la creo nuova

		if (restrictSchoolsAndDayCare) {  //Applica restrizioni scolastiche
			restrictions.restrict("2020-03-04", Restriction.of(0), "school");
		}

		if (restrictUniversities) { //Applica restrizioni uni
			restrictions.restrict("2020-03-04", Restriction.of(0), "university");
		}

		if(restrictNoEssentials) { //Applico restrizioni su non essenziali e limito gli essenziali (smart-working)
			restrictions.restrict("2020-03-09", Restriction.of(0), "work_noEss","refreshment", "shop_noEss", "turism","sport", "leisure", "night");
			restrictions.restrict("2020-03-09", Restriction.of(0.4), "work_ess", "business", "shop_ess");
		}

		restrictions.restrict("2020-02-21",Restriction.ofCiCorrection(0.7),"home"); //Riduzione del 30% del contatto anche in casa

		for (Map.Entry<String, Double> e : ciCorrections.entrySet()) {  //Applico correzione contatto a tutte le attività

			String date = e.getKey();
			Double ciCorrection = e.getValue();
			restrictions.restrict(date, Restriction.ofCiCorrection(ciCorrection), AbstractSnzScenario2020_Milan.DEFAULT_ACTIVITIES);
			restrictions.restrict(date, Restriction.ofCiCorrection(ciCorrection), "pt");
		}

		if (maskCompliance == 0) return restrictions;    //Se non ho l'obbligo di uso mascherine, interrompo qui

		LocalDate masksCenterDate = LocalDate.of(2020, 3, 9);  //Giorno in cui entra l'obbligo mascherine
		double clothFraction = maskCompliance * 0.9;
		double surgicalFraction = maskCompliance * 0.1;

		for (int ii = 0; ii <= introductionPeriod; ii++) {  //Funzione che introduce gradualmente l'uso di mascherine
			LocalDate date = masksCenterDate.plusDays(-introductionPeriod / 2 + ii);
			restrictions.restrict(date, Restriction.ofMask(Map.of(FaceMask.CLOTH, clothFraction * ii / introductionPeriod,
					FaceMask.SURGICAL, surgicalFraction * ii / introductionPeriod)), AbstractSnzScenario2020_Milan.DEFAULT_ACTIVITIES);
		}

		return restrictions;
	}

	@Provides
	@Singleton
	public Config config() { //Prima funzione avviata

		Config config = AbstractSnzScenario2020_Milan.getBaseConfig(); //Crea struttura base
		config.facilities().setInputFile(INPUT.resolve("facilities0.25.xml.gz").toString()); //Importo facilities.xml
		config.vehicles().setVehiclesFile(INPUT.resolve("output_vehicles.xml.gz").toString()); //Importa i tipi di veicolo da file
		config.households().setInputFile(INPUT.resolve("output_households.xml.gz").toString()); //Importo households.xml

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class); //Config episim (contenitore)

		episimConfig.setInputEventsFile(INPUT.resolve("output_events-0.25.xml.gz").toString()); //Importa eventi da file

		episimConfig.setInitialInfections(5); //Dichiara 5 agenti come infetti iniziali
		episimConfig.setSampleSize(0.25); //Dichiara la grandezza del sample (1 => 100 persone = 100 agenti)
		episimConfig.setMaxContacts(3); //Num massimo contatti di un agente al giorno
		String startDate = "2020-02-16";
		episimConfig.setStartDate(startDate);
		episimConfig.setProgressionConfig(baseProgressionConfig(Transition.config()).build()); 

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class); //Config tracciamento
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(1); //Giorni dopo il quale inizia l'attività di tracciamento e di quarantena (Giorni passati tra tracciamento e quarantena)
		double tracingProbability = 0.5;
		tracingConfig.setTracingProbability(tracingProbability); //Probabilità di tracciamento sull'intera simulazione
		tracingConfig.setTracingPeriod_days(3); //quanti giorni antecedenti alla rilevazione vengono tracciati
		tracingConfig.setMinContactDuration_sec(15 * 60.);   //Durata minima contatto utile considerata per la tracciabilità
		tracingConfig.setQuarantineHouseholdMembers(true);  //Metti i membri di casa (household) in quarantena
		tracingConfig.setEquipmentRate(0.6); //Probabilitò che una persona abbia un dispositivo di tracciamento
		tracingConfig.setTracingDelay_days(2);  //Tempo utile per la tracciabilità completa
		tracingConfig.setTracingCapacity_pers_per_day(Map.of(      //Data inizio tracciamento e quante persone vengono tracciate al giorno
				LocalDate.of(2020, 2, 21), 5,
				LocalDate.of(2020,2,24),15,
				LocalDate.of(2020,2,28),25,
				LocalDate.of(2020,3,5),35,
				LocalDate.of(2020,3,10),40,
				LocalDate.of(2020,3,15),50,
				LocalDate.of(2020,3,20),60,
				LocalDate.of(2020,3,30),65,
				LocalDate.of(2020,4,15),75,
				LocalDate.of(2020,4,30),100
		));

		BasePolicyBuilder basePolicyBuilder = new BasePolicyBuilder(episimConfig);   //Creazione politica sulla base di episimConfig

		episimConfig.setPolicy(FixedPolicy.class, basePolicyBuilder.build().build());

		config.controler().setOutputDirectory("./output-ZonaRossa_Tracciamento");

		return config;
	}

	public static class BasePolicyBuilder {
		private final EpisimConfigGroup episimConfig;

		private Map<String, Double> ciCorrections = Map.of("2020-03-01", 0.32);  //correzione % contatto
		private long introductionPeriod = 14;  // legato alle mascherine
		private double maskCompliance = 0.95;  // % uso mascherina
		private boolean restrictSchoolsAndDayCare = true; //Chiusura scuole
		private boolean restrictUniversities = true; //Chiusura uni
		private ActivityParticipation activityParticipation;  //Null viene gestito
		private boolean restrictNoEssentials = true;

		public BasePolicyBuilder(EpisimConfigGroup episimConfig) {
			this.episimConfig = episimConfig;
			this.activityParticipation = new CreateRestrictionsFromCSV(episimConfig);
		}

		public void setIntroductionPeriod(long introductionPeriod) {
			this.introductionPeriod = introductionPeriod;
		}

		public void setMaskCompliance(double maskCompliance) {
			this.maskCompliance = maskCompliance;
		}

		public void setActivityParticipation(ActivityParticipation activityParticipation) {
			this.activityParticipation = activityParticipation;
		}

		public ActivityParticipation getActivityParticipation() {
			return activityParticipation;
		}

		public Map<String, Double> getCiCorrections() {
			return ciCorrections;
		}

		public boolean getRestrictSchoolsAndDayCare() {
			return restrictSchoolsAndDayCare;
		}

		public void setRestrictSchoolsAndDayCare(boolean restrictSchoolsAndDayCare) {
			this.restrictSchoolsAndDayCare = restrictSchoolsAndDayCare;
		}
		public boolean getRestrictUniversities() {
			return restrictUniversities;
		}

		public void setRestrictUniversities(boolean restrictUniversities) {
			this.restrictUniversities = restrictUniversities;
		}

		public ConfigBuilder build() {
			ConfigBuilder configBuilder = null;
			try {
				configBuilder = basePolicy(activityParticipation, ciCorrections,introductionPeriod,   //Crea policy base fornendo i valori scritti
						maskCompliance, restrictSchoolsAndDayCare, restrictUniversities, restrictNoEssentials);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return configBuilder;
		}
	}

}
