/*
 * SORMAS® - Surveillance Outbreak Response Management & Analysis System
 * Copyright © 2016-2020 Helmholtz-Zentrum für Infektionsforschung GmbH (HZI)
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package de.symeda.sormas.backend.caze;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.http.HttpStatus;
import org.hamcrest.MatcherAssert;
import org.hibernate.internal.SessionImpl;
import org.hibernate.query.spi.QueryImplementor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

import de.symeda.sormas.api.CaseMeasure;
import de.symeda.sormas.api.Disease;
import de.symeda.sormas.api.Language;
import de.symeda.sormas.api.VisitOrigin;
import de.symeda.sormas.api.activityascase.ActivityAsCaseDto;
import de.symeda.sormas.api.activityascase.ActivityAsCaseType;
import de.symeda.sormas.api.caze.CaseClassification;
import de.symeda.sormas.api.caze.CaseCriteria;
import de.symeda.sormas.api.caze.CaseDataDto;
import de.symeda.sormas.api.caze.CaseExportDto;
import de.symeda.sormas.api.caze.CaseExportType;
import de.symeda.sormas.api.caze.CaseIndexDetailedDto;
import de.symeda.sormas.api.caze.CaseIndexDto;
import de.symeda.sormas.api.caze.CaseLogic;
import de.symeda.sormas.api.caze.CaseOutcome;
import de.symeda.sormas.api.caze.CasePersonDto;
import de.symeda.sormas.api.caze.CaseReferenceDto;
import de.symeda.sormas.api.caze.InvestigationStatus;
import de.symeda.sormas.api.caze.MapCaseDto;
import de.symeda.sormas.api.caze.VaccinationInfoSource;
import de.symeda.sormas.api.caze.VaccinationStatus;
import de.symeda.sormas.api.caze.Vaccine;
import de.symeda.sormas.api.caze.VaccineManufacturer;
import de.symeda.sormas.api.caze.surveillancereport.SurveillanceReportDto;
import de.symeda.sormas.api.clinicalcourse.ClinicalVisitDto;
import de.symeda.sormas.api.clinicalcourse.HealthConditionsDto;
import de.symeda.sormas.api.common.DeletionDetails;
import de.symeda.sormas.api.common.DeletionReason;
import de.symeda.sormas.api.contact.ContactCriteria;
import de.symeda.sormas.api.contact.ContactDto;
import de.symeda.sormas.api.contact.ContactReferenceDto;
import de.symeda.sormas.api.contact.FollowUpStatus;
import de.symeda.sormas.api.document.DocumentDto;
import de.symeda.sormas.api.document.DocumentRelatedEntityType;
import de.symeda.sormas.api.epidata.EpiDataDto;
import de.symeda.sormas.api.event.EventCriteria;
import de.symeda.sormas.api.event.EventDto;
import de.symeda.sormas.api.event.EventInvestigationStatus;
import de.symeda.sormas.api.event.EventParticipantDto;
import de.symeda.sormas.api.event.EventReferenceDto;
import de.symeda.sormas.api.event.EventStatus;
import de.symeda.sormas.api.exposure.ExposureDto;
import de.symeda.sormas.api.exposure.ExposureType;
import de.symeda.sormas.api.externalsurveillancetool.ExternalSurveillanceToolFacade;
import de.symeda.sormas.api.externalsurveillancetool.ExternalSurveillanceToolRuntimeException;
import de.symeda.sormas.api.hospitalization.PreviousHospitalizationDto;
import de.symeda.sormas.api.i18n.I18nProperties;
import de.symeda.sormas.api.i18n.Strings;
import de.symeda.sormas.api.immunization.ImmunizationDto;
import de.symeda.sormas.api.immunization.ImmunizationManagementStatus;
import de.symeda.sormas.api.immunization.ImmunizationStatus;
import de.symeda.sormas.api.immunization.MeansOfImmunization;
import de.symeda.sormas.api.importexport.ExportConfigurationDto;
import de.symeda.sormas.api.importexport.ImportExportUtils;
import de.symeda.sormas.api.infrastructure.community.CommunityReferenceDto;
import de.symeda.sormas.api.infrastructure.district.DistrictDto;
import de.symeda.sormas.api.infrastructure.district.DistrictReferenceDto;
import de.symeda.sormas.api.infrastructure.facility.FacilityReferenceDto;
import de.symeda.sormas.api.infrastructure.facility.FacilityType;
import de.symeda.sormas.api.infrastructure.facility.FacilityTypeGroup;
import de.symeda.sormas.api.infrastructure.region.RegionReferenceDto;
import de.symeda.sormas.api.messaging.MessageType;
import de.symeda.sormas.api.person.CauseOfDeath;
import de.symeda.sormas.api.person.PersonContactDetailDto;
import de.symeda.sormas.api.person.PersonContactDetailType;
import de.symeda.sormas.api.person.PersonDto;
import de.symeda.sormas.api.person.PersonReferenceDto;
import de.symeda.sormas.api.person.PhoneNumberType;
import de.symeda.sormas.api.person.PresentCondition;
import de.symeda.sormas.api.person.Sex;
import de.symeda.sormas.api.sample.AdditionalTestDto;
import de.symeda.sormas.api.sample.PathogenTestDto;
import de.symeda.sormas.api.sample.PathogenTestResultType;
import de.symeda.sormas.api.sample.PathogenTestType;
import de.symeda.sormas.api.sample.SampleCriteria;
import de.symeda.sormas.api.sample.SampleDto;
import de.symeda.sormas.api.sample.SampleMaterial;
import de.symeda.sormas.api.share.ExternalShareStatus;
import de.symeda.sormas.api.symptoms.SymptomState;
import de.symeda.sormas.api.symptoms.SymptomsDto;
import de.symeda.sormas.api.task.TaskContext;
import de.symeda.sormas.api.task.TaskCriteria;
import de.symeda.sormas.api.task.TaskDto;
import de.symeda.sormas.api.task.TaskStatus;
import de.symeda.sormas.api.task.TaskType;
import de.symeda.sormas.api.therapy.PrescriptionDto;
import de.symeda.sormas.api.therapy.TherapyDto;
import de.symeda.sormas.api.therapy.TreatmentDto;
import de.symeda.sormas.api.travelentry.TravelEntryDto;
import de.symeda.sormas.api.user.DefaultUserRole;
import de.symeda.sormas.api.user.UserDto;
import de.symeda.sormas.api.user.UserReferenceDto;
import de.symeda.sormas.api.utils.DataHelper;
import de.symeda.sormas.api.utils.DateHelper;
import de.symeda.sormas.api.utils.OutdatedEntityException;
import de.symeda.sormas.api.utils.SortProperty;
import de.symeda.sormas.api.utils.UtilDate;
import de.symeda.sormas.api.utils.YesNoUnknown;
import de.symeda.sormas.api.utils.criteria.ExternalShareDateType;
import de.symeda.sormas.api.vaccination.VaccinationDto;
import de.symeda.sormas.api.visit.VisitCriteria;
import de.symeda.sormas.api.visit.VisitDto;
import de.symeda.sormas.api.visit.VisitIndexDto;
import de.symeda.sormas.api.visit.VisitStatus;
import de.symeda.sormas.backend.AbstractBeanTest;
import de.symeda.sormas.backend.MockProducer;
import de.symeda.sormas.backend.TestDataCreator.RDCF;
import de.symeda.sormas.backend.TestDataCreator.RDCFEntities;
import de.symeda.sormas.backend.caze.CaseFacadeEjb.CaseFacadeEjbLocal;
import de.symeda.sormas.backend.infrastructure.district.District;
import de.symeda.sormas.backend.infrastructure.region.Region;
import de.symeda.sormas.backend.share.ExternalShareInfo;
import de.symeda.sormas.backend.util.DtoHelper;

public class CaseFacadeEjbTest extends AbstractBeanTest {

	private static final int WIREMOCK_TESTING_PORT = 8888;
	private ExternalSurveillanceToolFacade subjectUnderTest;

	@Rule
	public WireMockRule wireMockRule = new WireMockRule(options().port(WIREMOCK_TESTING_PORT), false);

	@Rule
	public final ExpectedException exception = ExpectedException.none();

	@Before
	public void setup() {
		configureExternalSurvToolUrlForWireMock();
		subjectUnderTest = getExternalSurveillanceToolGatewayFacade();
	}

	@After
	public void teardown() {
		clearExternalSurvToolUrlForWireMock();
	}

	@Test
	public void testFilterByResponsibleRegionAndDistrictOfCase() {

		RDCFEntities rdcf = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		RDCFEntities rdcf2 = creator.createRDCFEntities("Region2", "District2", "Community2", "Facility2");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
		PersonDto cazePerson = creator.createPerson("Case", "Person", Sex.MALE, 1980, 1, 1);
		CaseDataDto caze = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);
		SampleDto caseSample = creator.createSample(caze.toReference(), user.toReference(), rdcf.facility);

		ContactDto contact = creator.createContact(
			user.toReference(),
			null,
			creator.createPerson("John", "Smith").toReference(),
			caze,
			new Date(),
			new Date(),
			Disease.CORONAVIRUS,
			null);
		SampleDto contactSample =
			creator.createSample(contact.toReference(), new Date(), new Date(), user.toReference(), SampleMaterial.BLOOD, rdcf.facility);

		ContactDto otherContact = creator.createContact(user.toReference(), creator.createPerson("John", "Doe").toReference(), new Date());
		SampleDto otherContactSample =
			creator.createSample(otherContact.toReference(), new Date(), new Date(), user.toReference(), SampleMaterial.BLOOD, rdcf.facility);
		otherContact.setResultingCase(caze.toReference());
		getContactFacade().save(otherContact);

		caze.setRegion(new RegionReferenceDto(rdcf2.region.getUuid(), null, null));
		caze.setDistrict(new DistrictReferenceDto(rdcf2.district.getUuid(), null, null));
		caze.setCommunity(new CommunityReferenceDto(rdcf2.community.getUuid(), null, null));
		caze.setHealthFacility(new FacilityReferenceDto(rdcf2.facility.getUuid(), null, null));

		getCaseFacade().save(caze);

		final CaseCriteria caseCriteria = new CaseCriteria().region(new RegionReferenceDto(rdcf.region.getUuid(), null, null))
			.district(new DistrictReferenceDto(rdcf.district.getUuid(), null, null));
		Assert.assertEquals(1, getCaseFacade().getIndexList(caseCriteria, 0, 100, null).size());

		Assert.assertEquals(3, getSampleFacade().getIndexList(new SampleCriteria(), 0, 100, null).size());

		final SampleCriteria sampleCriteria = new SampleCriteria().region(new RegionReferenceDto(rdcf.region.getUuid(), null, null))
			.district(new DistrictReferenceDto(rdcf.district.getUuid(), null, null));
		Assert.assertEquals(2, getSampleFacade().getIndexList(sampleCriteria, 0, 100, null).size());

		Assert.assertEquals(1, getTaskFacade().getIndexList(new TaskCriteria(), 0, 100, null).size());

		final TaskCriteria taskCriteria = new TaskCriteria().region(new RegionReferenceDto(rdcf.region.getUuid(), null, null))
			.district(new DistrictReferenceDto(rdcf.district.getUuid(), null, null));
		Assert.assertEquals(1, getTaskFacade().getIndexList(taskCriteria, 0, 100, null).size());

		Assert.assertEquals(2, getContactFacade().getIndexList(new ContactCriteria(), 0, 100, null).size());

		final ContactCriteria contactCriteriaRdcf2 = new ContactCriteria().region(new RegionReferenceDto(rdcf2.region.getUuid(), null, null))
			.district(new DistrictReferenceDto(rdcf2.district.getUuid(), null, null))
			.community(new CommunityReferenceDto(rdcf2.community.getUuid(), null, null));
		Assert.assertEquals(1, getContactFacade().getIndexList(contactCriteriaRdcf2, 0, 100, null).size());

		final ContactCriteria contactCriteriaRdcf1 = new ContactCriteria().region(new RegionReferenceDto(rdcf.region.getUuid(), null, null))
			.district(new DistrictReferenceDto(rdcf.district.getUuid(), null, null));
		Assert.assertEquals(1, getContactFacade().getIndexList(contactCriteriaRdcf1, 0, 100, null).size());
	}

	@Test
	public void testGetCasesForDuplicateMerging() {

		final Date today = new Date();
		final Date threeDaysAgo = DateUtils.addDays(today, -3);

		RDCFEntities rdcf = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
		PersonDto cazePerson = creator.createPerson("Case", "Person", Sex.MALE, 1980, 1, 1);
		CaseDataDto caze = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			today,
			rdcf);

		SessionImpl em = (SessionImpl) getEntityManager();
		QueryImplementor query = em.createQuery("select c from cases c where c.uuid=:uuid");
		query.setParameter("uuid", caze.getUuid());
		Case singleResult = (Case) query.getSingleResult();

		singleResult.setCreationDate(new Timestamp(threeDaysAgo.getTime()));
		singleResult.setReportDate(threeDaysAgo);
		em.save(singleResult);

		PersonDto cazePerson2 = creator.createPerson("Case", "Person", Sex.MALE, 1980, 1, 1);
		CaseDataDto case2 = creator.createCase(
			user.toReference(),
			cazePerson2.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			DateUtils.addMinutes(today, -3),
			rdcf);

		final List<CaseIndexDto[]> casesForDuplicateMergingToday =
			getCaseFacade().getCasesForDuplicateMerging(new CaseCriteria().creationDateFrom(today).creationDateTo(today), true);
		final List<CaseIndexDto[]> casesForDuplicateMergingThreeDaysAgo =
			getCaseFacade().getCasesForDuplicateMerging(new CaseCriteria().creationDateFrom(threeDaysAgo).creationDateTo(threeDaysAgo), true);
		Assert.assertEquals(1, casesForDuplicateMergingToday.size());
		Assert.assertEquals(1, casesForDuplicateMergingThreeDaysAgo.size());
	}

	@Test
	public void testGetDuplicateCasesOfSameSexAndDifferentBirthDateIsEmpty() {

		final Date today = new Date();

		RDCFEntities rdcf = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
		PersonDto cazePerson = creator.createPerson("Case", "Person", Sex.MALE, 1980, 1, 1);
		creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			today,
			rdcf);

		PersonDto cazePerson2 = creator.createPerson("Case", "Person", Sex.MALE, 1980, 1, 2);
		creator.createCase(
			user.toReference(),
			cazePerson2.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			DateUtils.addMinutes(today, -3),
			rdcf);

		Assert.assertEquals(
			0,
			getCaseFacade().getCasesForDuplicateMerging(new CaseCriteria().creationDateFrom(today).creationDateTo(today), true).size());
	}

	@Test
	public void testGetDuplicateCasesOfDifferentSexAndSameBirthDateIsEmpty() {

		final Date today = new Date();

		RDCFEntities rdcf = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
		PersonDto cazePerson = creator.createPerson("Case", "Person", Sex.MALE, 1980, 1, 1);
		creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			today,
			rdcf);

		PersonDto cazePerson2 = creator.createPerson("Case", "Person", Sex.FEMALE, 1980, 1, 1);
		creator.createCase(
			user.toReference(),
			cazePerson2.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			DateUtils.addMinutes(today, -3),
			rdcf);

		Assert.assertEquals(
			0,
			getCaseFacade().getCasesForDuplicateMerging(new CaseCriteria().creationDateFrom(today).creationDateTo(today), true).size());
	}

	@Test
	public void testGetDuplicateCasesOfSexUnknownAndSameBirthDateMatches() {

		final Date today = new Date();

		RDCFEntities rdcf = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
		PersonDto cazePerson = creator.createPerson("Case", "Person", Sex.MALE, 1980, 1, 1);
		creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			today,
			rdcf);

		PersonDto cazePerson2 = creator.createPerson("Case", "Person", Sex.UNKNOWN, 1980, 1, 1);
		creator.createCase(
			user.toReference(),
			cazePerson2.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			DateUtils.addMinutes(today, -3),
			rdcf);

		Assert.assertEquals(
			1,
			getCaseFacade().getCasesForDuplicateMerging(new CaseCriteria().creationDateFrom(today).creationDateTo(today), true).size());
	}

	@Test
	public void testDiseaseChangeUpdatesContacts() {

		RDCFEntities rdcf = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
		PersonDto cazePerson = creator.createPerson("Case", "Person");
		CaseDataDto caze = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);
		PersonDto contactPerson = creator.createPerson("Contact", "Person");
		ContactDto contact =
			creator.createContact(user.toReference(), user.toReference(), contactPerson.toReference(), caze, new Date(), new Date(), null);

		// Follow-up status and duration should be set to the requirements for EVD
		assertEquals(FollowUpStatus.FOLLOW_UP, contact.getFollowUpStatus());
		assertEquals(LocalDate.now().plusDays(21), UtilDate.toLocalDate(contact.getFollowUpUntil()));

		caze.setDisease(Disease.MEASLES);
		getCaseFacade().save(caze);

		// Follow-up status and duration should be set to no follow-up and null
		// respectively because
		// Measles does not require a follow-up
		contact = getContactFacade().getByUuid(contact.getUuid());
		assertEquals(FollowUpStatus.NO_FOLLOW_UP, contact.getFollowUpStatus());
		assertNull(contact.getFollowUpUntil());
	}

	@Test
	public void testCountCasesWithMisingContactInformation() {
		RDCF rdcf = creator.createRDCF("Region", "District", "Community", "Facility");
		RDCFEntities newRDCF = creator.createRDCFEntities("New Region", "New District", "New Community", "New Facility");
		UserDto user = useSurveillanceOfficerLogin(rdcf);

		PersonDto cazePerson = creator.createPerson("Case", "Person");
		CaseDataDto caze = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);

		Assert.assertEquals(1, getCaseFacade().countCasesWithMissingContactInformation(Arrays.asList(caze.getUuid()), MessageType.SMS));

		cazePerson.setPhone("40742140797");
		getPersonFacade().save(cazePerson);

		Assert.assertEquals(0, getCaseFacade().countCasesWithMissingContactInformation(Arrays.asList(caze.getUuid()), MessageType.SMS));
	}

	@Test
	public void testMovingCaseUpdatesTaskAssigneeAndCreatesPreviousHospitalization() {

		RDCF rdcf = creator.createRDCF("Region", "District", "Community", "Facility");
		RDCFEntities newRDCF = creator.createRDCFEntities("New Region", "New District", "New Community", "New Facility");
		UserDto caseOfficer = creator.createUser(
			newRDCF.region.getUuid(),
			newRDCF.district.getUuid(),
			newRDCF.facility.getUuid(),
			"Case",
			"Officer",
			creator.getUserRoleReference(DefaultUserRole.CASE_OFFICER));

		UserDto user = creator.createUser(
				rdcf.region.getUuid(),
				rdcf.district.getUuid(),
				rdcf.facility.getUuid(),
				"Surv",
				"Off",
				creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_OFFICER));
		loginWith(user);

		PersonDto cazePerson = creator.createPerson("Case", "Person");
		CaseDataDto caze = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);
		TaskDto pendingTask = creator.createTask(
			TaskContext.CASE,
			TaskType.CASE_INVESTIGATION,
			TaskStatus.PENDING,
			caze.toReference(),
			null,
			null,
			new Date(),
			user.toReference());
		TaskDto doneTask = creator.createTask(
			TaskContext.CASE,
			TaskType.CASE_INVESTIGATION,
			TaskStatus.DONE,
			caze.toReference(),
			null,
			null,
			new Date(),
			user.toReference());

		caze.setResponsibleRegion(new RegionReferenceDto(newRDCF.region.getUuid(), null, null));
		caze.setResponsibleDistrict(new DistrictReferenceDto(newRDCF.district.getUuid(), null, null));
		caze.setResponsibleCommunity(new CommunityReferenceDto(newRDCF.community.getUuid(), null, null));

		caze.setRegion(new RegionReferenceDto(newRDCF.region.getUuid(), null, null));
		caze.setDistrict(new DistrictReferenceDto(newRDCF.district.getUuid(), null, null));
		caze.setCommunity(new CommunityReferenceDto(newRDCF.community.getUuid(), null, null));

		caze.setHealthFacility(new FacilityReferenceDto(newRDCF.facility.getUuid(), null, null));
		caze.setSurveillanceOfficer(caseOfficer.toReference());
		CaseDataDto oldCase = getCaseFacade().getCaseDataByUuid(caze.getUuid());
		CaseLogic.handleHospitalization(caze, oldCase, true);
		getCaseFacade().save(caze);

		caze = getCaseFacade().getCaseDataByUuid(caze.getUuid());
		pendingTask = getTaskFacade().getByUuid(pendingTask.getUuid());
		doneTask = getTaskFacade().getByUuid(doneTask.getUuid());

		// Case should have the new region, district, community and facility set
		assertEquals(caze.getRegion().getUuid(), newRDCF.region.getUuid());
		assertEquals(caze.getDistrict().getUuid(), newRDCF.district.getUuid());
		assertEquals(caze.getCommunity().getUuid(), newRDCF.community.getUuid());
		assertEquals(caze.getHealthFacility().getUuid(), newRDCF.facility.getUuid());

		// Pending task is reassigned to the case officer
		// Done task is not reassigned
		assertEquals(pendingTask.getAssigneeUser().getUuid(), caseOfficer.getUuid());
		assertEquals(doneTask.getAssigneeUser().getUuid(), user.getUuid());

		// A previous hospitalization with the former facility should have been created
		List<PreviousHospitalizationDto> previousHospitalizations = caze.getHospitalization().getPreviousHospitalizations();
		assertEquals(1, previousHospitalizations.size());
	}

	@Test
	public void testMapCaseListCreation() {

		RDCFEntities rdcf = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
		PersonDto cazePerson = creator.createPerson("Case", "Person", p -> {
			p.getAddress().setLatitude(0.0);
			p.getAddress().setLongitude(0.0);
		});
		CaseDataDto caze = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);

		Long count = getCaseFacade().countCasesForMap(
			caze.getRegion(),
			caze.getDistrict(),
			caze.getDisease(),
			DateHelper.subtractDays(new Date(), 1),
			DateHelper.addDays(new Date(), 1),
			null);

		List<MapCaseDto> mapCaseDtos = getCaseFacade().getCasesForMap(
			caze.getRegion(),
			caze.getDistrict(),
			caze.getDisease(),
			DateHelper.subtractDays(new Date(), 1),
			DateHelper.addDays(new Date(), 1),
			null);

		// List should have one entry
		assertEquals((long) count, mapCaseDtos.size());
		assertEquals(1, mapCaseDtos.size());
	}

	@Test
	public void testGetIndexList() {

		String districtName = "District";
		RDCF rdcf = creator.createRDCF("Region", districtName, "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
		useSurveillanceOfficerLogin(rdcf);

		String lastName = "Person";
		PersonDto cazePerson = creator.createPerson("Case", lastName);
		creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);
		creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf,
			c -> c.setHealthFacilityDetails("abc"));
		creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf,
			c -> c.setHealthFacilityDetails("xyz"));

		List<CaseIndexDto> results = getCaseFacade().getIndexList(
			null,
			0,
			100,
			Arrays.asList(
				new SortProperty(CaseIndexDto.DISEASE),
				new SortProperty(CaseIndexDto.PERSON_FIRST_NAME),
				new SortProperty(CaseIndexDto.RESPONSIBLE_DISTRICT_NAME),
				new SortProperty(CaseIndexDto.HEALTH_FACILITY_NAME, false),
				new SortProperty(CaseIndexDto.SURVEILLANCE_OFFICER_UUID)));

		// List should have one entry
		assertEquals(3, results.size());

		assertEquals(districtName, results.get(0).getResponsibleDistrictName());
		assertEquals(lastName, results.get(0).getPersonLastName());
		assertEquals("Facility - xyz", results.get(0).getHealthFacilityName());
		assertEquals("Facility - abc", results.get(1).getHealthFacilityName());
		assertEquals("Facility", results.get(2).getHealthFacilityName());
	}

	@Test
	public void testGetIndexListByFreeText() {

		String districtName = "District";
		RDCF rdcf = creator.createRDCF("Region", districtName, "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
		useSurveillanceOfficerLogin(rdcf);

		PersonDto person1 = creator.createPerson("FirstName1", "LastName1", p -> {
			p.getAddress().setPostalCode("10115");
			p.getAddress().setCity("Berlin");
			p.setPhone("+4930-90-1820");
		});
		creator.createCase(
			user.toReference(),
			person1.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);

		PersonDto person2 = creator.createPerson("FirstName2", "LastName2", p -> {
			p.getAddress().setPostalCode("20095");
			p.getAddress().setCity("Hamburg");
			p.setPhone("+49-30-901822");
		});
		creator.createCase(
			user.toReference(),
			person2.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);

		PersonDto person3 = creator.createPerson("FirstName3", "Last Name3", p -> {
			p.getAddress().setPostalCode("80331");
			p.getAddress().setCity("Munich");
			p.setPhone("+49 31 9018 20");
		});
		creator.createCase(
			user.toReference(),
			person3.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);

		Assert.assertEquals(3, getCaseFacade().getIndexList(null, 0, 100, null).size());
		Assert.assertEquals(1, getCaseFacade().getIndexList(new CaseCriteria().personLike("Munich"), 0, 100, null).size());
		Assert.assertEquals(1, getCaseFacade().getIndexList(new CaseCriteria().personLike("Last Name3"), 0, 100, null).size());
		Assert.assertEquals(1, getCaseFacade().getIndexList(new CaseCriteria().personLike("20095"), 0, 100, null).size());
		Assert.assertEquals(2, getCaseFacade().getIndexList(new CaseCriteria().personLike("+49-31-901-820"), 0, 100, null).size());
		Assert.assertEquals(1, getCaseFacade().getIndexList(new CaseCriteria().personLike("4930901822"), 0, 100, null).size());
	}

	@Test
	public void testGetIndexListByEventFreeText() {

		String districtName = "District";
		RDCF rdcf = creator.createRDCF("Region", districtName, "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
		useSurveillanceOfficerLogin(rdcf);

		PersonDto person1 = creator.createPerson();
		PersonDto person2 = creator.createPerson();

		EventDto event1 = creator.createEvent(
			EventStatus.SIGNAL,
			EventInvestigationStatus.PENDING,
			"Signal foo",
			"A long description for this event",
			user.toReference(),
			null,
			null);

		EventParticipantDto event1Participant1 = creator.createEventParticipant(event1.toReference(), person1, user.toReference());
		EventParticipantDto event1Participant2 = creator.createEventParticipant(event1.toReference(), person2, user.toReference());

		CaseDataDto case1 = creator.createCase(
			user.toReference(),
			person1.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);

		creator.createCase(
			user.toReference(),
			person2.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);
		event1Participant1.setResultingCase(case1.toReference());
		getEventParticipantFacade().save(event1Participant1);

		Assert.assertEquals(2, getCaseFacade().getIndexList(null, 0, 100, null).size());
		Assert.assertEquals(1, getCaseFacade().getIndexList(new CaseCriteria().eventLike("signal"), 0, 100, null).size());
		Assert.assertEquals(1, getCaseFacade().getIndexList(new CaseCriteria().eventLike(event1.getUuid()), 0, 100, null).size());
		Assert.assertEquals(1, getCaseFacade().getIndexList(new CaseCriteria().eventLike("signal description"), 0, 100, null).size());
	}

	@Test
	public void testCaseExportWithPrescriptionsTreatmentsVisits() {
		RDCFEntities rdcfEntities = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		UserDto user = createUser(rdcfEntities);

		PersonDto cazePerson = creator.createPerson("Case", "Person");
		CaseDataDto caze = createCase(user, cazePerson, rdcfEntities);
		cazePerson.getAddress().setCity("City");
		getPersonFacade().save(cazePerson);

		ExposureDto exposure = ExposureDto.build(ExposureType.TRAVEL);
		exposure.getLocation().setDetails("Ghana");
		exposure.setStartDate(new Date());
		exposure.setEndDate(new Date());
		caze.getEpiData().getExposures().add(exposure);
		caze.getSymptoms().setAbdominalPain(SymptomState.YES);
		caze = getCaseFacade().save(caze);
		creator.createPrescription(caze);
		creator.createTreatment(caze);
		creator.createTreatment(caze);
		creator.createClinicalVisit(caze);

		getPersonFacade().save(cazePerson);

		List<CaseExportDto> results =
			getCaseFacade().getExportList(new CaseCriteria(), Collections.emptySet(), CaseExportType.CASE_MANAGEMENT, 0, 100, null, Language.EN);
		assertEquals(1, results.size());
		CaseExportDto exportDto = results.get(0);
		assertNotNull(exportDto.getSymptoms());
		assertEquals(1, exportDto.getNumberOfPrescriptions());
		assertEquals(2, exportDto.getNumberOfTreatments());
		assertEquals(1, exportDto.getNumberOfClinicalVisits());
	}

	@Test
	public void testCaseExportWithSamples() {
		RDCFEntities rdcfEntities = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		RDCF rdcf = new RDCF(rdcfEntities);
		UserDto user = createUser(rdcfEntities);

		PersonDto cazePerson = creator.createPerson("Case", "Person");
		CaseDataDto caze = createCase(user, cazePerson, rdcfEntities);
		cazePerson.getAddress().setCity("City");
		getPersonFacade().save(cazePerson);

		creator.createSample(caze.toReference(), user.toReference(), rdcf.facility);
		creator.createSample(caze.toReference(), user.toReference(), rdcf.facility);
		creator.createSample(caze.toReference(), user.toReference(), rdcf.facility);
		SampleDto sample = creator.createSample(caze.toReference(), user.toReference(), rdcf.facility);
		SampleDto lastSample = creator.createSample(caze.toReference(), user.toReference(), rdcf.facility);

		lastSample.setPathogenTestResult(PathogenTestResultType.NEGATIVE);

		List<CaseExportDto> results =
			getCaseFacade().getExportList(new CaseCriteria(), Collections.emptySet(), CaseExportType.CASE_SURVEILLANCE, 0, 100, null, Language.EN);
		assertEquals(1, results.size());
		CaseExportDto exportDto = results.get(0);
		assertNotNull(exportDto.getSymptoms());

		assertEquals(lastSample.getUuid(), exportDto.getSample1().getUuid());
		assertEquals("Facility", exportDto.getSample1().getLab());
		assertEquals(lastSample.getPathogenTestResult(), PathogenTestResultType.NEGATIVE);

		assertEquals(sample.getUuid(), exportDto.getSample2().getUuid());
		assertEquals("Facility", exportDto.getSample2().getLab());
		assertEquals(sample.getPathogenTestResult(), PathogenTestResultType.PENDING);
		assertEquals(sample.getPathogenTestResult(), PathogenTestResultType.PENDING);

		assertEquals(2, exportDto.getOtherSamples().size());
		assertEquals("Facility", exportDto.getOtherSamples().get(0).getLab());
	}

	@Test
	public void testGetExportListWithRelevantVaccinations() {
		RDCFEntities rdcfEntities = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		RDCF rdcf = new RDCF(rdcfEntities);
		UserDto user = createUser(rdcfEntities);

		PersonDto cazePerson = creator.createPerson("Case", "Person");
		CaseDataDto caze = createCase(user, cazePerson, rdcfEntities);
		cazePerson.getAddress().setCity("City");
		getPersonFacade().save(cazePerson);

		ExposureDto exposure = ExposureDto.build(ExposureType.TRAVEL);
		exposure.getLocation().setDetails("Ghana");
		exposure.setStartDate(new Date());
		exposure.setEndDate(new Date());
		caze.getEpiData().getExposures().add(exposure);
		caze.getSymptoms().setAbdominalPain(SymptomState.YES);
		caze = getCaseFacade().save(caze);
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, -1);
		creator.createSample(caze.toReference(), new Date(), new Date(), user.toReference(), SampleMaterial.BLOOD, rdcfEntities.facility);
		creator.createSample(caze.toReference(), cal.getTime(), cal.getTime(), user.toReference(), SampleMaterial.CRUST, rdcfEntities.facility);
		creator.createPathogenTest(caze, PathogenTestType.ANTIGEN_DETECTION, PathogenTestResultType.POSITIVE);
		creator.createPrescription(caze);
		ImmunizationDto immunization = creator.createImmunization(
			caze.getDisease(),
			caze.getPerson(),
			caze.getReportingUser(),
			ImmunizationStatus.ACQUIRED,
			MeansOfImmunization.VACCINATION,
			ImmunizationManagementStatus.COMPLETED,
			rdcf,
			DateHelper.subtractDays(new Date(), 10),
			DateHelper.subtractDays(new Date(), 5),
			DateHelper.subtractDays(new Date(), 1),
			null);
		creator.createImmunization(
			caze.getDisease(),
			caze.getPerson(),
			caze.getReportingUser(),
			ImmunizationStatus.ACQUIRED,
			MeansOfImmunization.VACCINATION,
			ImmunizationManagementStatus.COMPLETED,
			rdcf,
			DateHelper.subtractDays(new Date(), 8),
			DateHelper.subtractDays(new Date(), 7),
			null,
			null);

		VaccinationDto firstVaccination = creator.createVaccinationWithDetails(
			caze.getReportingUser(),
			immunization.toReference(),
			HealthConditionsDto.build(),
			DateHelper.subtractDays(new Date(), 7),
			Vaccine.OXFORD_ASTRA_ZENECA,
			VaccineManufacturer.ASTRA_ZENECA,
			VaccinationInfoSource.UNKNOWN,
			"inn1",
			"123",
			"code123",
			"3");
		VaccinationDto secondVaccination = creator.createVaccinationWithDetails(
			caze.getReportingUser(),
			immunization.toReference(),
			HealthConditionsDto.build(),
			DateHelper.subtractDays(new Date(), 4),
			Vaccine.MRNA_1273,
			VaccineManufacturer.MODERNA,
			VaccinationInfoSource.UNKNOWN,
			"inn2",
			"456",
			"code456",
			"2");
		VaccinationDto thirdVaccination = creator.createVaccinationWithDetails(
			caze.getReportingUser(),
			immunization.toReference(),
			HealthConditionsDto.build(),
			new Date(),
			Vaccine.COMIRNATY,
			VaccineManufacturer.BIONTECH_PFIZER,
			VaccinationInfoSource.UNKNOWN,
			"inn3",
			"789",
			"code789",
			"1");

		final String primaryPhone = "0000444888";
		final String primaryEmail = "primary@email.com";
		cazePerson = getPersonFacade().getByUuid(cazePerson.getUuid());
		cazePerson.setPhone(primaryPhone);
		cazePerson.setEmailAddress(primaryEmail);

		cazePerson.getPersonContactDetails()
			.add(
				PersonContactDetailDto.build(
					cazePerson.toReference(),
					false,
					PersonContactDetailType.PHONE,
					PhoneNumberType.LANDLINE,
					"",
					"0265590500",
					"",
					false,
					"",
					""));
		cazePerson.getPersonContactDetails()
			.add(
				PersonContactDetailDto
					.build(cazePerson.toReference(), false, PersonContactDetailType.EMAIL, null, "", "secondary@email.com", "", false, "", ""));
		cazePerson.getPersonContactDetails()
			.add(
				PersonContactDetailDto
					.build(cazePerson.toReference(), false, PersonContactDetailType.OTHER, null, "SkypeID", "personSkype", "", false, "", ""));

		getPersonFacade().save(cazePerson);

		List<CaseExportDto> results =
			getCaseFacade().getExportList(new CaseCriteria(), Collections.emptySet(), CaseExportType.CASE_SURVEILLANCE, 0, 100, null, Language.EN);

		// List should have one entry
		assertEquals(1, results.size());

		assertTrue(results.get(0).getSampleDateTime2().after(results.get(0).getSampleDateTime3()));
		// Make sure that everything that is added retrospectively (symptoms, sample
		// dates, lab results, address, travel history) is present
		CaseExportDto exportDto = results.get(0);
		assertNotNull(exportDto.getSymptoms());
//		assertNotNull(exportDto.getSampleDateTime1());
//		assertNotNull(exportDto.getSampleLab1());
//		assertTrue(StringUtils.isNotEmpty(exportDto.getAddress()));
//		assertTrue(StringUtils.isNotEmpty(exportDto.getTravelHistory()));
		assertEquals(primaryPhone, exportDto.getPhone());
		assertEquals(primaryEmail, exportDto.getEmailAddress());
		final String otherContactDetails = exportDto.getOtherContactDetails();
		assertTrue(otherContactDetails.contains("0265590500 (PHONE)"));
		assertTrue(otherContactDetails.contains("secondary@email.com (EMAIL)"));
		assertTrue(otherContactDetails.contains("personSkype (SkypeID)"));
		assertEquals(VaccinationStatus.VACCINATED, exportDto.getVaccinationStatus());
		assertEquals(firstVaccination.getVaccinationDate(), exportDto.getFirstVaccinationDate());
		assertEquals(secondVaccination.getVaccineName(), exportDto.getVaccineName());
		assertEquals(secondVaccination.getVaccinationDate(), exportDto.getLastVaccinationDate());
		assertEquals(secondVaccination.getVaccinationInfoSource(), exportDto.getVaccinationInfoSource());
		assertEquals(secondVaccination.getVaccineInn(), exportDto.getVaccineInn());
		assertEquals(secondVaccination.getVaccineBatchNumber(), exportDto.getVaccineBatchNumber());
		assertEquals(secondVaccination.getVaccineAtcCode(), exportDto.getVaccineAtcCode());
		assertEquals(secondVaccination.getVaccineDose(), exportDto.getNumberOfDoses());

		// Test with full export columns
		results = getCaseFacade().getExportList(
			new CaseCriteria(),
			Collections.emptySet(),
			CaseExportType.CASE_SURVEILLANCE,
			0,
			100,
			createFullExportConfig(),
			Language.EN);
		assertThat(results, hasSize(1));
	}

	@Test
	public void testGetExportListWithoutRelevantVaccinations() {
		RDCFEntities rdcfEntities = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		RDCF rdcf = new RDCF(rdcfEntities);
		UserDto user = createUser(rdcfEntities);

		getOutbreakFacade().startOutbreak(rdcf.district, Disease.CORONAVIRUS);

		PersonDto cazePerson = creator.createPerson("Case", "Person");
		CaseDataDto caze = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.CORONAVIRUS,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			DateHelper.addDays(new Date(), 1),
			rdcfEntities);

		caze.setRegion(rdcf.region);
		caze.setDistrict(rdcf.district);
		cazePerson.getAddress().setCity("City");
		getPersonFacade().save(cazePerson);

		caze.getSymptoms().setAbdominalPain(SymptomState.YES);
		caze = getCaseFacade().save(caze);

		ImmunizationDto immunization = creator.createImmunization(
			caze.getDisease(),
			caze.getPerson(),
			caze.getReportingUser(),
			ImmunizationStatus.ACQUIRED,
			MeansOfImmunization.VACCINATION,
			ImmunizationManagementStatus.COMPLETED,
			rdcf,
			DateHelper.subtractDays(new Date(), 10),
			DateHelper.subtractDays(new Date(), 5),
			DateHelper.subtractDays(new Date(), 1),
			null);

		VaccinationDto vaccination = creator.createVaccinationWithDetails(
			caze.getReportingUser(),
			immunization.toReference(),
			HealthConditionsDto.build(),
			DateHelper.addDays(new Date(), 1),
			Vaccine.MRNA_1273,
			VaccineManufacturer.MODERNA,
			VaccinationInfoSource.UNKNOWN,
			"inn2",
			"456",
			"code456",
			"2");

		cazePerson = getPersonFacade().getByUuid(cazePerson.getUuid());
		getPersonFacade().save(cazePerson);

		List<CaseExportDto> results =
			getCaseFacade().getExportList(new CaseCriteria(), Collections.emptySet(), CaseExportType.CASE_SURVEILLANCE, 0, 100, null, Language.EN);

		// List should have one entry
		assertEquals(1, results.size());
		CaseExportDto exportDto = results.get(0);

		assertEquals(I18nProperties.getString(Strings.yes), exportDto.getAssociatedWithOutbreak());
		assertNull(exportDto.getFirstVaccinationDate());
		assertNull(exportDto.getVaccineName());
		assertNull(exportDto.getLastVaccinationDate());
		assertNull(exportDto.getVaccinationInfoSource());
		assertNull(exportDto.getVaccineInn());
		assertNull(exportDto.getVaccineBatchNumber());
		assertNull(exportDto.getVaccineAtcCode());
		assertEquals(exportDto.getNumberOfDoses(), "");
	}

	/**
	 * Assure that n cardinalities do not duplicate case
	 */
	@Test
	public void testGetExportListNoDuplicates() {

		RDCFEntities rdcf = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));

		PersonDto cazePerson = creator.createPerson("Case", "Person", p -> {
			p.getAddress().setLatitude(50.0);
			p.getAddress().setLongitude(10.0);
			p.getAddress().setLatLonAccuracy(3F);
		});
		cazePerson.getAddress().setCity("City");
		getPersonFacade().save(cazePerson);

		CaseDataDto caze = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);

		caze.getEpiData().setExposureDetailsKnown(YesNoUnknown.YES);

		{
			ExposureDto exposure = ExposureDto.build(ExposureType.TRAVEL);
			exposure.getLocation().setDetails("Ghana");
			exposure.setStartDate(new Date());
			exposure.setEndDate(new Date());
			caze.getEpiData().getExposures().add(exposure);
		}
		{
			ExposureDto exposure = ExposureDto.build(ExposureType.TRAVEL);
			exposure.getLocation().setDetails("Nigeria");
			exposure.setStartDate(new Date());
			exposure.setEndDate(new Date());
			caze.getEpiData().getExposures().add(exposure);
		}

		caze = getCaseFacade().save(caze);

		List<CaseExportDto> result =
			getCaseFacade().getExportList(new CaseCriteria(), Collections.emptySet(), CaseExportType.CASE_SURVEILLANCE, 0, 100, null, Language.EN);
		assertThat(result, hasSize(1));
		CaseExportDto exportDto = result.get(0);
		assertNotNull(exportDto.getEpiDataId());
		assertEquals("50.0, 10.0 +-3m", exportDto.getAddressGpsCoordinates());
		assertThat(exportDto.getUuid(), equalTo(caze.getUuid()));
		assertTrue(exportDto.isTraveled());
	}

	@Test
	public void testGeRelevantCasesForVaccination() {
		RDCFEntities rdcfEntities = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		RDCF rdcf = new RDCF(rdcfEntities);
		UserDto user = createUser(rdcfEntities);

		PersonDto cazePerson1 = creator.createPerson("Case1", "Person1");
		CaseDataDto caze1 = createCase(user, cazePerson1, rdcfEntities);

		ImmunizationDto immunization = creator.createImmunization(
			caze1.getDisease(),
			caze1.getPerson(),
			caze1.getReportingUser(),
			ImmunizationStatus.ACQUIRED,
			MeansOfImmunization.VACCINATION,
			ImmunizationManagementStatus.COMPLETED,
			rdcf,
			DateHelper.subtractDays(new Date(), 10),
			DateHelper.subtractDays(new Date(), 5),
			DateHelper.subtractDays(new Date(), 1),
			null);

		VaccinationDto firstRelevantVaccinationForCase1 = creator.createVaccinationWithDetails(
			caze1.getReportingUser(),
			immunization.toReference(),
			HealthConditionsDto.build(),
			DateHelper.subtractDays(new Date(), 7),
			Vaccine.OXFORD_ASTRA_ZENECA,
			VaccineManufacturer.ASTRA_ZENECA,
			VaccinationInfoSource.UNKNOWN,
			"inn1",
			"123",
			"code123",
			"3");

		VaccinationDto notRelevantVaccinationForCase1 = creator.createVaccinationWithDetails(
			caze1.getReportingUser(),
			immunization.toReference(),
			HealthConditionsDto.build(),
			new Date(),
			Vaccine.MRNA_1273,
			VaccineManufacturer.MODERNA,
			VaccinationInfoSource.UNKNOWN,
			"inn2",
			"456",
			"code456",
			"2");

		List<CaseDataDto> cases1 = new ArrayList<>();
		cases1.add(caze1);
		assertEquals(getCaseFacade().getRelevantCasesForVaccination(firstRelevantVaccinationForCase1), cases1);
		assertTrue(getCaseFacade().getRelevantCasesForVaccination(notRelevantVaccinationForCase1).isEmpty());
	}

	/**
	 * Test with {@link CaseExportType#CASE_MANAGEMENT} and full export columns.
	 */
	@Test
	public void testGetExportListCaseManagement() {

		// 0. Run without data
		List<CaseExportDto> result = getCaseFacade()
			.getExportList(new CaseCriteria(), Collections.emptySet(), CaseExportType.CASE_MANAGEMENT, 0, 100, createFullExportConfig(), Language.EN);
		assertThat(result, is(empty()));
	}

	private ExportConfigurationDto createFullExportConfig() {

		boolean withFollowUp = true;
		boolean withClinicalCourse = true;
		boolean withTherapy = true;
		String countryLocale = getConfigFacade().getCountryLocale();

		ExportConfigurationDto config = new ExportConfigurationDto();
		config.setProperties(
			ImportExportUtils.getCaseExportProperties((a, b) -> "Case", withFollowUp, withClinicalCourse, withTherapy, countryLocale)
				.stream()
				.map(e -> e.getPropertyId())
				.collect(Collectors.toSet()));

		return config;
	}

	@Test
	public void testGetExportListWithoutDeletedSamples() {
		RDCFEntities rdcfEntities = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		RDCF rdcf = new RDCF(rdcfEntities);
		UserDto user = createUser(rdcfEntities);

		PersonDto cazePerson = creator.createPerson("Case", "Person");
		CaseDataDto caze = createCase(user, cazePerson, rdcfEntities);
		cazePerson.getAddress().setCity("City");
		getPersonFacade().save(cazePerson);

		SampleDto sample1 = creator.createSample(caze.toReference(), user.toReference(), rdcf.facility);
		SampleDto sample2 = creator.createSample(caze.toReference(), user.toReference(), rdcf.facility);
		getSampleFacade().deleteSample(sample1.toReference(), new DeletionDetails(DeletionReason.OTHER_REASON, "test reason"));

		List<CaseExportDto> results =
			getCaseFacade().getExportList(new CaseCriteria(), Collections.emptySet(), CaseExportType.CASE_SURVEILLANCE, 0, 100, null, Language.EN);

		// List should have one entry
		assertEquals(1, results.size());
		CaseExportDto exportDto = results.get(0);

		assertEquals(exportDto.getSample1().getUuid(), sample2.getUuid());
		assertNull(exportDto.getSample2().getUuid());
		assertNull(exportDto.getSample3().getUuid());
	}

	@Test
	public void testCaseDeletion() throws ExternalSurveillanceToolRuntimeException {
		Date since = new Date();

		RDCFEntities rdcf = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
		UserDto admin = getUserFacade().getByUserName("AdMin");

		PersonDto cazePerson = creator.createPerson("Case", "Person");
		CaseDataDto caze = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);
		PersonDto contactPerson = creator.createPerson("Contact", "Person");
		ContactDto contact =
			creator.createContact(user.toReference(), user.toReference(), contactPerson.toReference(), caze, new Date(), new Date(), null);
		TaskDto task = creator.createTask(
			TaskContext.CASE,
			TaskType.CASE_INVESTIGATION,
			TaskStatus.PENDING,
			caze.toReference(),
			null,
			null,
			new Date(),
			user.toReference());
		SampleDto sample = creator.createSample(caze.toReference(), new Date(), new Date(), user.toReference(), SampleMaterial.BLOOD, rdcf.facility);
		SampleDto sampleAssociatedToContactAndCase =
			creator.createSample(caze.toReference(), new Date(), new Date(), user.toReference(), SampleMaterial.BLOOD, rdcf.facility);
		ContactDto contact2 =
			creator.createContact(user.toReference(), user.toReference(), contactPerson.toReference(), caze, new Date(), new Date(), null);
		sampleAssociatedToContactAndCase.setAssociatedContact(new ContactReferenceDto(contact2.getUuid()));
		getSampleFacade().saveSample(sampleAssociatedToContactAndCase);

		PathogenTestDto pathogenTest = creator.createPathogenTest(sample.toReference(), caze);
		AdditionalTestDto additionalTest = creator.createAdditionalTest(sample.toReference());

		// Database should contain the created case, contact, task and sample
		assertNotNull(getCaseFacade().getCaseDataByUuid(caze.getUuid()));
		assertNotNull(getContactFacade().getByUuid(contact.getUuid()));
		assertNotNull(getSampleFacade().getSampleByUuid(sample.getUuid()));
		assertNotNull(getSampleTestFacade().getByUuid(pathogenTest.getUuid()));
		assertNotNull(getAdditionalTestFacade().getByUuid(additionalTest.getUuid()));
		assertNotNull(getTaskFacade().getByUuid(task.getUuid()));

		getCaseFacade().delete(caze.getUuid(), new DeletionDetails(DeletionReason.OTHER_REASON, "test reason"));

		// Deleted flag should be set for case, sample and pathogen test; Additional test should be deleted; Contact should not have the deleted flag; Task should not be deleted
		assertTrue(getCaseFacade().getDeletedUuidsSince(since).contains(caze.getUuid()));
		assertFalse(getContactFacade().getDeletedUuidsSince(since).contains(contact.getUuid()));
		assertTrue(getSampleFacade().getDeletedUuidsSince(since).contains(sample.getUuid()));
		assertFalse(getSampleFacade().getDeletedUuidsSince(since).contains(sampleAssociatedToContactAndCase.getUuid()));
		assertTrue(getSampleTestFacade().getDeletedUuidsSince(since).contains(pathogenTest.getUuid()));
		assertNotNull(getAdditionalTestFacade().getByUuid(additionalTest.getUuid()));
		assertNotNull(getTaskFacade().getByUuid(task.getUuid()));
		assertEquals(DeletionReason.OTHER_REASON, getCaseFacade().getByUuid(caze.getUuid()).getDeletionReason());
		assertEquals("test reason", getCaseFacade().getByUuid(caze.getUuid()).getOtherDeletionReason());
	}

	@Test
	public void testOutcomePersonConditionUpdate() {

		final Date today = new Date();

		RDCFEntities rdcf = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
		PersonDto cazePerson = creator.createPerson("Case", "Person");
		CaseDataDto firstCase = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			today,
			rdcf);

		// case deceased -> person should be set to dead, causeofdeath(disease) filled in
		firstCase.setOutcome(CaseOutcome.DECEASED);
		firstCase = getCaseFacade().save(firstCase);
		cazePerson = getPersonFacade().getByUuid(cazePerson.getUuid());

		assertNull(firstCase.getOutcomeDate());
		assertEquals(PresentCondition.DEAD, cazePerson.getPresentCondition());
		assertEquals(CauseOfDeath.EPIDEMIC_DISEASE, cazePerson.getCauseOfDeath());
		assertEquals(firstCase.getDisease(), cazePerson.getCauseOfDeathDisease());

		// update just the outcomeDate -> person should also get the deathdate set
		firstCase.setOutcomeDate(today);
		firstCase = getCaseFacade().save(firstCase);
		cazePerson = getPersonFacade().getByUuid(cazePerson.getUuid());

		assertTrue(DateHelper.isSameDay(firstCase.getOutcomeDate(), cazePerson.getDeathDate()));

		// case has no outcome again -> person should be alive
		firstCase.setOutcome(CaseOutcome.NO_OUTCOME);
		firstCase = getCaseFacade().save(firstCase);
		cazePerson = getPersonFacade().getByUuid(cazePerson.getUuid());
		assertNull(firstCase.getOutcomeDate());
		assertEquals(PresentCondition.UNKNOWN, cazePerson.getPresentCondition());
		assertNull(cazePerson.getDeathDate());

		// additional, newer cases for the the person
		firstCase.setReportDate(DateHelper.subtractDays(today, 17));
		firstCase.getSymptoms().setOnsetDate(firstCase.getReportDate());
		firstCase = getCaseFacade().save(firstCase);
		CaseDataDto secondCase = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			DateHelper.subtractDays(today, 12),
			rdcf);
		secondCase.setOutcome(CaseOutcome.RECOVERED);
		secondCase.setOutcomeDate(DateHelper.subtractDays(today, 10));
		secondCase = getCaseFacade().save(secondCase);
		CaseDataDto thirdCase = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.MEASLES,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			DateHelper.subtractDays(today, 7),
			rdcf);
		thirdCase.setOutcome(CaseOutcome.DECEASED);
		thirdCase.setOutcomeDate(DateHelper.subtractDays(today, 5));
		thirdCase = getCaseFacade().save(thirdCase);

		// the newest case is set to deceased -> person should be dead
		cazePerson = getPersonFacade().getByUuid(cazePerson.getUuid());
		firstCase = getCaseFacade().getCaseDataByUuid(firstCase.getUuid());
		secondCase = getCaseFacade().getCaseDataByUuid(secondCase.getUuid());
		assertEquals(PresentCondition.DEAD, cazePerson.getPresentCondition());
		assertEquals(CauseOfDeath.EPIDEMIC_DISEASE, cazePerson.getCauseOfDeath());
		assertEquals(thirdCase.getDisease(), cazePerson.getCauseOfDeathDisease());
		assertEquals(CaseOutcome.NO_OUTCOME, firstCase.getOutcome());
		assertEquals(CaseOutcome.RECOVERED, secondCase.getOutcome());
		assertEquals(CaseOutcome.DECEASED, thirdCase.getOutcome());
		assertTrue(DateHelper.isSameDay(cazePerson.getDeathDate(), thirdCase.getOutcomeDate()));

		// person alive again -> deceased case has to be set to no outcome
		cazePerson.setPresentCondition(PresentCondition.ALIVE);
		cazePerson = getPersonFacade().save(cazePerson);
		firstCase = getCaseFacade().getCaseDataByUuid(firstCase.getUuid());
		secondCase = getCaseFacade().getCaseDataByUuid(secondCase.getUuid());
		thirdCase = getCaseFacade().getCaseDataByUuid(thirdCase.getUuid());
		assertEquals(CaseOutcome.NO_OUTCOME, firstCase.getOutcome());
		assertEquals(CaseOutcome.RECOVERED, secondCase.getOutcome());
		assertEquals(CaseOutcome.NO_OUTCOME, thirdCase.getOutcome());
		assertNull(thirdCase.getOutcomeDate());

		// move 1st and 3rd case to past, so that they should no longer be affected by anything
		firstCase.setReportDate(DateHelper.subtractDays(today, 100));
		firstCase.getSymptoms().setOnsetDate(firstCase.getReportDate());
		thirdCase.setReportDate(DateHelper.subtractDays(today, 100));
		thirdCase.getSymptoms().setOnsetDate(thirdCase.getReportDate());
		getCaseFacade().save(firstCase);
		getCaseFacade().save(thirdCase);

		// Set 2nd Case to deceased again
		secondCase.setOutcome(CaseOutcome.DECEASED);
		secondCase = getCaseFacade().save(secondCase);
		cazePerson = getPersonFacade().getByUuid(cazePerson.getUuid());

		// manually set the persons deathdate to 32 days in the past
		cazePerson.setDeathDate(DateHelper.subtractDays(secondCase.getReportDate(), 32));
		cazePerson = getPersonFacade().save(cazePerson);

		// Change Case to RECOVERD -> person should not change, because deathdate is over 30 days away from case reportdate
		secondCase.setOutcome(CaseOutcome.RECOVERED);
		secondCase = getCaseFacade().save(secondCase);
		cazePerson = getPersonFacade().getByUuid(cazePerson.getUuid());

		assertEquals(PresentCondition.DEAD, cazePerson.getPresentCondition());

		// update the present condition to dead -> case should still not be affected because of the date threshold
		cazePerson.setPresentCondition(PresentCondition.DEAD);
		cazePerson.setDeathDate(today);
		cazePerson.setCauseOfDeath(CauseOfDeath.EPIDEMIC_DISEASE);
		cazePerson.setCauseOfDeathDisease(secondCase.getDisease());
		getPersonFacade().save(cazePerson);
		secondCase = getCaseFacade().getCaseDataByUuid(secondCase.getUuid());

		assertEquals(CaseOutcome.RECOVERED, secondCase.getOutcome());
	}

	@Test
	public void testOutcomePersonConditionUpdateForAppSync() throws InterruptedException {

		RDCFEntities rdcf = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
		PersonDto cazePerson = creator.createPerson("Case", "Person");
		CaseDataDto firstCase = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);

		// simulate short delay between transmissions
		Thread.sleep(DtoHelper.CHANGE_DATE_TOLERANCE_MS + 1);

		// set person to dead so that case will be updated automatically
		cazePerson.setPresentCondition(PresentCondition.DEAD);
		cazePerson.setDeathDate(new Date());
		cazePerson.setCauseOfDeath(CauseOfDeath.EPIDEMIC_DISEASE);
		cazePerson.setCauseOfDeathDisease(firstCase.getDisease());
		getPersonFacade().save(cazePerson);

		// this should throw an exception
		exception.expect(OutdatedEntityException.class);
		getCaseFacade().save(firstCase);
	}

	@Test
	public void testArchiveAndDearchiveCase() {
		RDCFEntities rdcf = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
		PersonDto cazePerson = creator.createPerson("Case", "Person");
		CaseDataDto caze = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);
		Date testStartDate = new Date();

		// getAllActiveCases and getAllUuids should return length 1
		assertEquals(1, getCaseFacade().getAllAfter(null).size());
		assertEquals(1, getCaseFacade().getAllActiveUuids().size());

		stubFor(
			post(urlEqualTo("/export")).withRequestBody(containing(caze.getUuid()))
				.withRequestBody(containing("caseUuids"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK)));

		Case case1 = getCaseService().getByUuid(caze.getUuid());
		getExternalShareInfoService().createAndPersistShareInfo(case1, ExternalShareStatus.SHARED);
		getCaseFacade().archive(caze.getUuid(), null);

		// getAllActiveCases and getAllUuids should return length 0
		assertEquals(0, getCaseFacade().getAllAfter(null).size());
		assertEquals(0, getCaseFacade().getAllActiveUuids().size());

		// getArchivedUuidsSince should return length 1
		assertEquals(1, getCaseFacade().getArchivedUuidsSince(testStartDate).size());

		getCaseFacade().dearchive(Collections.singletonList(caze.getUuid()), null);

		// getAllActiveCases and getAllUuids should return length 1
		assertEquals(1, getCaseFacade().getAllAfter(null).size());
		assertEquals(1, getCaseFacade().getAllActiveUuids().size());

		// getArchivedUuidsSince should return length 0
		assertEquals(0, getCaseFacade().getArchivedUuidsSince(testStartDate).size());
	}

	@Test
	public void testGetAllActiveCasesDoesNotIncludeExtendedChangeDateFiltersSample() throws InterruptedException {

		RDCFEntities rdcf = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
		PersonDto cazePerson = creator.createPerson("Case", "Person");
		CaseDataDto caze = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);

		SampleDto sample = creator.createSample(caze.toReference(), user.toReference(), rdcf.facility);

		Date date = new Date();
		//the delay is needed in order to ensure the time difference between the date and the case dependent objects update
		Thread.sleep(10L);

		sample.setComment("one comment");
		getSampleFacade().saveSample(sample);

		assertEquals(0, getCaseFacade().getAllAfter(date).size());
	}

	@Test
	public void testGenerateEpidNumber() throws ExternalSurveillanceToolRuntimeException {

		RDCFEntities rdcf = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR),
			creator.getUserRoleReference(DefaultUserRole.ADMIN));
		PersonDto cazePerson = creator.createPerson("Case", "Person");
		CaseDataDto caze = creator.createCase(user.toReference(), cazePerson.toReference(), rdcf);

		Calendar calendar = Calendar.getInstance();
		String year = String.valueOf(calendar.get(Calendar.YEAR)).substring(2);

		assertEquals("COU-REG-DIS-" + year + "-001", caze.getEpidNumber());

		CaseDataDto secondCaze = creator.createCase(user.toReference(), cazePerson.toReference(), rdcf);

		assertEquals("COU-REG-DIS-" + year + "-002", secondCaze.getEpidNumber());

		secondCaze.setEpidNumber("COU-REG-DIS-" + year + "-0004");
		getCaseFacade().save(secondCaze);

		CaseDataDto thirdCaze = creator.createCase(user.toReference(), cazePerson.toReference(), rdcf);

		assertEquals("COU-REG-DIS-" + year + "-005", thirdCaze.getEpidNumber());

		thirdCaze.setEpidNumber("COU-REG-DIS-" + year + "-3");
		getCaseFacade().save(thirdCaze);

		CaseDataDto fourthCaze = creator.createCase(user.toReference(), cazePerson.toReference(), rdcf);

		assertEquals("COU-REG-DIS-" + year + "-005", fourthCaze.getEpidNumber());

		fourthCaze.setEpidNumber("COU-REG-DIS-" + year + "-AAA");
		getCaseFacade().save(fourthCaze);
		fourthCaze = getCaseFacade().getCaseDataByUuid(fourthCaze.getUuid());

		assertEquals("COU-REG-DIS-" + year + "-005", fourthCaze.getEpidNumber());

		// Make sure that deleted cases are ignored when searching for the highest existing epid nummber
		getCaseFacade().delete(fourthCaze.getUuid(), new DeletionDetails(DeletionReason.OTHER_REASON, "test reason"));

		CaseDataDto fifthCaze = creator.createCase(user.toReference(), cazePerson.toReference(), rdcf);

		assertEquals("COU-REG-DIS-" + year + "-005", fifthCaze.getEpidNumber());

	}

	@Test
	public void testMergeCase() throws IOException {

		// 1. Create

		// Create leadCase
		UserDto leadUser = creator.createUser("", "", "", "First", "User");
		UserReferenceDto leadUserReference = new UserReferenceDto(leadUser.getUuid());
		PersonDto leadPerson = creator.createPerson("Alex", "Miller");
		PersonReferenceDto leadPersonReference = new PersonReferenceDto(leadPerson.getUuid());
		RDCF leadRdcf = creator.createRDCF();
		CaseDataDto leadCase = creator.createCase(
			leadUserReference,
			leadPersonReference,
			Disease.DENGUE,
			CaseClassification.SUSPECT,
			InvestigationStatus.PENDING,
			new Date(),
			leadRdcf,
			(c) -> {
				c.setAdditionalDetails("Test additional details");
				c.setFollowUpComment("Test followup comment");
			});
		leadCase.setPregnant(YesNoUnknown.UNKNOWN);
		leadCase.getEpiData().setActivityAsCaseDetailsKnown(YesNoUnknown.NO);
		getCaseFacade().save(leadCase);
		VisitDto leadVisit = creator.createVisit(leadCase.getDisease(), leadCase.getPerson(), leadCase.getReportDate());
		leadVisit.getSymptoms().setAnorexiaAppetiteLoss(SymptomState.YES);
		getVisitFacade().saveVisit(leadVisit);

		// Create otherCase
		UserDto otherUser = creator.createUser("", "", "", "Second", "User");
		UserReferenceDto otherUserReference = new UserReferenceDto(otherUser.getUuid());
		PersonDto otherPerson = creator.createPerson("Max", "Smith");
		otherPerson.setBirthWeight(2);
		getPersonFacade().save(otherPerson);
		PersonReferenceDto otherPersonReference = new PersonReferenceDto(otherPerson.getUuid());
		RDCF otherRdcf = creator.createRDCF("Reg2", "Dis2", "Comm2", "Fac2", "Poe2");
		CaseDataDto otherCase = creator.createCase(
			otherUserReference,
			otherPersonReference,
			Disease.CHOLERA,
			CaseClassification.SUSPECT,
			InvestigationStatus.PENDING,
			new Date(),
			otherRdcf,
			(c) -> {
				c.setAdditionalDetails("Test other additional details");
				c.setFollowUpComment("Test other followup comment");
			});
		otherCase.setCaseIdIsm(12345);
		CaseReferenceDto otherCaseReference = getCaseFacade().getReferenceByUuid(otherCase.getUuid());
		ContactDto contact =
			creator.createContact(otherUserReference, otherUserReference, otherPersonReference, otherCase, new Date(), new Date(), null);
		Region region = creator.createRegion("");
		District district = creator.createDistrict("", region);
		SampleDto sample = creator.createSample(
			otherCaseReference,
			otherUserReference,
			creator.createFacility("", region, district, creator.createCommunity("", district)));
		TaskDto task = creator.createTask(
			TaskContext.CASE,
			TaskType.CASE_INVESTIGATION,
			TaskStatus.PENDING,
			otherCaseReference,
			new ContactReferenceDto(),
			new EventReferenceDto(),
			new Date(),
			otherUserReference);
		TreatmentDto treatment = creator.createTreatment(otherCase);
		PrescriptionDto prescription = creator.createPrescription(otherCase);
		ClinicalVisitDto visit = creator.createClinicalVisit(otherCase);

		otherCase.getEpiData().setActivityAsCaseDetailsKnown(YesNoUnknown.YES);
		final ArrayList<ActivityAsCaseDto> otherActivitiesAsCase = new ArrayList<>();
		ActivityAsCaseDto activityAsCaseDto = new ActivityAsCaseDto();
		activityAsCaseDto.setActivityAsCaseType(ActivityAsCaseType.GATHERING);
		otherActivitiesAsCase.add(activityAsCaseDto);
		otherCase.getEpiData().setActivitiesAsCase(otherActivitiesAsCase);

		getCaseFacade().save(otherCase);
		VisitDto otherVisit = creator.createVisit(otherCase.getDisease(), otherCase.getPerson(), otherCase.getReportDate());
		otherVisit.getSymptoms().setAbdominalPain(SymptomState.YES);
		getVisitFacade().saveVisit(otherVisit);
		EventDto event = creator.createEvent(otherUserReference);
		event.setDisease(otherCase.getDisease());
		getEventFacade().save(event);
		EventParticipantDto otherCaseEventParticipant = creator.createEventParticipant(event.toReference(), otherPerson, otherUserReference);
		otherCaseEventParticipant.setResultingCase(otherCaseReference);
		getEventParticipantFacade().save(otherCaseEventParticipant);

		creator.createSurveillanceReport(otherUserReference, otherCaseReference);
		TravelEntryDto travelEntry = creator.createTravelEntry(otherPersonReference, otherUserReference, otherRdcf, (t) -> {
			t.setDisease(otherCase.getDisease());
			t.setResultingCase(otherCaseReference);
		});

		DocumentDto document = creator.createDocument(
			leadUserReference,
			"document.pdf",
			"application/pdf",
			42L,
			DocumentRelatedEntityType.CASE,
			leadCase.getUuid(),
			"content".getBytes(StandardCharsets.UTF_8));
		DocumentDto otherDocument = creator.createDocument(
			leadUserReference,
			"other_document.pdf",
			"application/pdf",
			42L,
			DocumentRelatedEntityType.CASE,
			otherCase.getUuid(),
			"other content".getBytes(StandardCharsets.UTF_8));

		// 2. Merge

		getCaseFacade().mergeCase(leadCase.getUuid(), otherCase.getUuid());

		// 3. Test

		CaseDataDto mergedCase = getCaseFacade().getCaseDataByUuid(leadCase.getUuid());

		// Check no values
		assertNull(mergedCase.getClassificationComment());

		// Check 'lead and other have different values'
		assertEquals(leadCase.getDisease(), mergedCase.getDisease());

		// Check 'lead has value, other has not'
		assertEquals(leadCase.getPregnant(), mergedCase.getPregnant());

		// Check 'lead has no value, other has'
		assertEquals(otherCase.getCaseIdIsm(), mergedCase.getCaseIdIsm());

		PersonDto mergedPerson = getPersonFacade().getByUuid(mergedCase.getPerson().getUuid());

		// Check no values
		assertNull(mergedPerson.getBirthdateDD());

		// Check 'lead and other have different values'
		assertEquals(leadCase.getPerson().getFirstName(), mergedPerson.getFirstName());

		// Check 'lead has value, other has not'
		assertEquals(leadCase.getPerson().getLastName(), mergedPerson.getLastName());

		// Check 'lead has no value, other has'
		assertEquals(otherPerson.getBirthWeight(), mergedPerson.getBirthWeight());

		// Check merge comments
		assertEquals("Test additional details Test other additional details", mergedCase.getAdditionalDetails());
		assertEquals("Test followup comment Test other followup comment", mergedCase.getFollowUpComment());

		// 4. Test Reference Changes
		// 4.1 Contacts
		List<String> contactUuids = new ArrayList<>();
		contactUuids.add(contact.getUuid());
		assertEquals(leadCase.getUuid(), getContactFacade().getByUuids(contactUuids).get(0).getCaze().getUuid());

		// 4.2 Samples
		List<String> sampleUuids = new ArrayList<>();
		sampleUuids.add(sample.getUuid());
		assertEquals(leadCase.getUuid(), getSampleFacade().getByUuids(sampleUuids).get(0).getAssociatedCase().getUuid());

		// 4.3 Tasks
		List<String> taskUuids = new ArrayList<>();
		taskUuids.add(task.getUuid());
		assertEquals(leadCase.getUuid(), getTaskFacade().getByUuids(taskUuids).get(0).getCaze().getUuid());

		// 4.4 Treatments
		List<String> treatmentUuids = new ArrayList<>();
		treatmentUuids.add(treatment.getUuid());
		assertEquals(leadCase.getTherapy().getUuid(), getTreatmentFacade().getByUuids(treatmentUuids).get(0).getTherapy().getUuid());

		// 4.5 Prescriptions
		List<String> prescriptionUuids = new ArrayList<>();
		prescriptionUuids.add(prescription.getUuid());
		assertEquals(leadCase.getTherapy().getUuid(), getPrescriptionFacade().getByUuids(prescriptionUuids).get(0).getTherapy().getUuid());

		// 4.6 Clinical Visits
		List<String> visitUuids = new ArrayList<>();
		visitUuids.add(visit.getUuid());
		assertEquals(leadCase.getClinicalCourse().getUuid(), getClinicalVisitFacade().getByUuids(visitUuids).get(0).getClinicalCourse().getUuid());

		// 4.7 Visits;
		List<String> mergedVisits = getVisitFacade().getIndexList(new VisitCriteria().caze(mergedCase.toReference()), null, null, null)
			.stream()
			.map(VisitIndexDto::getUuid)
			.collect(Collectors.toList());
		assertEquals(2, mergedVisits.size());
		assertTrue(mergedVisits.contains(leadVisit.getUuid()));
		assertTrue(mergedVisits.contains(otherVisit.getUuid()));
		// and symptoms
		assertEquals(SymptomState.YES, mergedCase.getSymptoms().getAbdominalPain());
		assertEquals(SymptomState.YES, mergedCase.getSymptoms().getAnorexiaAppetiteLoss());
		assertTrue(mergedCase.getSymptoms().getSymptomatic());

		// 4.8 Linked Events
		assertEquals(1, getEventFacade().count(new EventCriteria().caze(mergedCase.toReference())));

		// 4.8 Linked Surveillance Reports
		List<SurveillanceReportDto> surveillanceReportList =
			getSurveillanceReportFacade().getByCaseUuids(Collections.singletonList(mergedCase.getUuid()));
		MatcherAssert.assertThat(surveillanceReportList, hasSize(1));

		// 5 Documents
		List<DocumentDto> mergedDocuments = getDocumentFacade().getDocumentsRelatedToEntity(DocumentRelatedEntityType.CASE, leadCase.getUuid());

		assertEquals(2, mergedDocuments.size());
		List<String> documentUuids = mergedDocuments.stream().map(DocumentDto::getUuid).collect(Collectors.toList());
		assertTrue(documentUuids.contains(document.getUuid()));
		assertTrue(documentUuids.contains(otherDocument.getUuid()));

		// 10 Activities as case
		final EpiDataDto epiData = mergedCase.getEpiData();
		assertEquals(YesNoUnknown.YES, epiData.getActivityAsCaseDetailsKnown());
		final List<ActivityAsCaseDto> activitiesAsCase = epiData.getActivitiesAsCase();
		assertEquals(1, activitiesAsCase.size());
		assertEquals(ActivityAsCaseType.GATHERING, activitiesAsCase.get(0).getActivityAsCaseType());

		// Travel entry
		travelEntry = getTravelEntryFacade().getByUuid(travelEntry.getUuid());
		assertEquals(mergedCase.toReference(), travelEntry.getResultingCase());
	}

	@Test
	public void testMergeCaseWithDuplicatedVaccines() {
		// Create leadCase
		RDCF rdcf = creator.createRDCF();
		UserDto leadUser = creator.createUser(rdcf, "First", "User");
		PersonDto leadPerson = creator.createPerson("Alex", "Miller");

		CaseDataDto leadCase = creator.createCase(
			leadUser.toReference(),
			leadPerson.toReference(),
			Disease.CORONAVIRUS,
			CaseClassification.SUSPECT,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);
		ImmunizationDto immunizationDto = creator.createImmunization(leadCase.getDisease(), leadPerson.toReference(), leadUser.toReference(), rdcf);
		HealthConditionsDto healthConditions = new HealthConditionsDto();
		Calendar calendar = Calendar.getInstance();
		calendar.set(2022, 6, 1);

		//vaccine without duplicate
		creator.createVaccinationWithDetails(
			leadUser.toReference(),
			immunizationDto.toReference(),
			healthConditions,
			new Date(),
			Vaccine.OXFORD_ASTRA_ZENECA,
			VaccineManufacturer.ASTRA_ZENECA,
			null,
			null,
			null,
			null,
			null);

		//pfizer vaccines duplicate
		VaccinationDto duplicateLeadVacc1 = creator.createVaccinationWithDetails(
			leadUser.toReference(),
			immunizationDto.toReference(),
			healthConditions,
			calendar.getTime(),
			Vaccine.COMIRNATY,
			VaccineManufacturer.BIONTECH_PFIZER,
			null,
			null,
			null,
			null,
			"dose1");
		VaccinationDto duplicateLeadVacc2 = creator.createVaccinationWithDetails(
			leadUser.toReference(),
			immunizationDto.toReference(),
			healthConditions,
			calendar.getTime(),
			Vaccine.COMIRNATY,
			VaccineManufacturer.BIONTECH_PFIZER,
			null,
			null,
			null,
			null,
			"dose2");

		//------------------------------------------------
		UserDto followUser = creator.createUser(rdcf, "Second", "User");
		PersonDto followPerson = creator.createPerson("Scott", "Miller");

		CaseDataDto followCase = creator.createCase(
			followUser.toReference(),
			followPerson.toReference(),
			Disease.CORONAVIRUS,
			CaseClassification.SUSPECT,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);

		ImmunizationDto followImmunizationDto =
			creator.createImmunization(followCase.getDisease(), followPerson.toReference(), followUser.toReference(), rdcf);
		//vaccine without duplicate
		creator.createVaccinationWithDetails(
			followUser.toReference(),
			followImmunizationDto.toReference(),
			healthConditions,
			new Date(),
			Vaccine.AD26_COV2_S,
			VaccineManufacturer.JOHNSON_JOHNSON,
			null,
			null,
			null,
			null,
			null);
		//set of duplicate vaccines ,also duplicate with the ones from lead case
		creator.createVaccinationWithDetails(
			followUser.toReference(),
			followImmunizationDto.toReference(),
			healthConditions,
			calendar.getTime(),
			Vaccine.COMIRNATY,
			VaccineManufacturer.BIONTECH_PFIZER,
			VaccinationInfoSource.ORAL_COMMUNICATION,
			"inn1",
			null,
			null,
			null);
		creator.createVaccinationWithDetails(
			followUser.toReference(),
			followImmunizationDto.toReference(),
			healthConditions,
			calendar.getTime(),
			Vaccine.COMIRNATY,
			VaccineManufacturer.BIONTECH_PFIZER,
			VaccinationInfoSource.ORAL_COMMUNICATION,
			null,
			null,
			"abc",
			null);
		creator.createVaccinationWithDetails(
			followUser.toReference(),
			followImmunizationDto.toReference(),
			healthConditions,
			calendar.getTime(),
			Vaccine.COMIRNATY,
			VaccineManufacturer.BIONTECH_PFIZER,
			VaccinationInfoSource.VACCINATION_CARD,
			null,
			"123",
			null,
			"dose99");

		assertEquals(null, duplicateLeadVacc1.getVaccinationInfoSource());
		assertEquals(null, duplicateLeadVacc2.getVaccinationInfoSource());

		getCaseFacade().mergeCase(leadCase.getUuid(), followCase.getUuid());

		List<VaccinationDto> mergedVaccines = getVaccinationFacade().getAllVaccinations(leadPerson.getUuid(), leadCase.getDisease());

		assertEquals(4, mergedVaccines.size());
		VaccinationDto mergedDuplciateVacc1 =
			mergedVaccines.stream().filter(v -> v.getUuid().equals(duplicateLeadVacc1.getUuid())).findFirst().orElse(null);
		assertEquals(null, mergedDuplciateVacc1.getVaccinationInfoSource());
		assertEquals("dose1", mergedDuplciateVacc1.getVaccineDose());

		VaccinationDto mergedDuplciateVacc2 =
			mergedVaccines.stream().filter(v -> v.getUuid().equals(duplicateLeadVacc2.getUuid())).findFirst().orElse(null);
		assertEquals(VaccinationInfoSource.VACCINATION_CARD, mergedDuplciateVacc2.getVaccinationInfoSource());
		assertEquals("123", mergedDuplciateVacc2.getVaccineBatchNumber());
		assertEquals("inn1", mergedDuplciateVacc2.getVaccineInn());
		assertEquals("dose2", mergedDuplciateVacc2.getVaccineDose());
		assertEquals("abc", mergedDuplciateVacc2.getVaccineAtcCode());
	}

	@Test
	public void testCloneCaseActivityAsCaseIsCloned() {

		// 1. Create

		// Create aCase
		UserDto user = creator.createUser("", "", "", "", "");
		useNationalUserLogin();
		UserReferenceDto userReferenceDto = new UserReferenceDto(user.getUuid());
		PersonDto person = creator.createPerson("Max", "Smith");
		person.setBirthWeight(2);
		getPersonFacade().save(person);
		PersonReferenceDto personReferenceDto = new PersonReferenceDto(person.getUuid());
		RDCF rdcf = creator.createRDCF();
		CaseDataDto aCase = creator.createCase(
			userReferenceDto,
			personReferenceDto,
			Disease.CHOLERA,
			CaseClassification.SUSPECT,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf,
			(c) -> {
				c.setAdditionalDetails("Test other additional details");
				c.setFollowUpComment("Test other followup comment");
			});

		aCase.getEpiData().setActivityAsCaseDetailsKnown(YesNoUnknown.YES);
		final ArrayList<ActivityAsCaseDto> otherActivitiesAsCase = new ArrayList<>();
		ActivityAsCaseDto activityAsCaseDto = new ActivityAsCaseDto();
		activityAsCaseDto.setActivityAsCaseType(ActivityAsCaseType.GATHERING);
		otherActivitiesAsCase.add(activityAsCaseDto);
		aCase.getEpiData().setActivitiesAsCase(otherActivitiesAsCase);

		CaseDataDto caseDataDto = getCaseFacade().save(aCase);

		// 2. Clone
		CaseDataDto clonedCase = getCaseFacade().cloneCase(caseDataDto);

		final EpiDataDto epiData = clonedCase.getEpiData();
		assertEquals(YesNoUnknown.YES, epiData.getActivityAsCaseDetailsKnown());
		final List<ActivityAsCaseDto> activitiesAsCase = epiData.getActivitiesAsCase();
		assertEquals(1, activitiesAsCase.size());
		assertEquals(ActivityAsCaseType.GATHERING, activitiesAsCase.get(0).getActivityAsCaseType());
	}

	@Test
	public void testCloneCaseWithOtherDieseseDontChangeOriginalCase() {
		RDCF rdcf = creator.createRDCF();
		UserDto user = creator.createUser(
			rdcf,
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR),
			creator.getUserRoleReference(DefaultUserRole.ADMIN));
		PersonDto cazePerson = creator.createPerson("Case", "Person");
		String diseaseDetails = "this is a test disease";
		CaseDataDto caze = creator.createCase(user.toReference(), cazePerson.toReference(), rdcf, c -> {
			c.setCaseClassification(CaseClassification.SUSPECT);
			c.setDisease(Disease.CORONAVIRUS);
			c.setDiseaseDetails(diseaseDetails);
		});

		// check values on original case
		CaseDataDto originalCase = getCaseFacade().getCaseDataByUuid(caze.getUuid());
		assertEquals(caze.getUuid(), originalCase.getUuid());
		assertEquals(Disease.CORONAVIRUS, originalCase.getDisease());
		assertEquals(CaseClassification.SUSPECT, originalCase.getCaseClassification());
		assertEquals(diseaseDetails, originalCase.getDiseaseDetails());
		assertEquals(caze.getPerson(), originalCase.getPerson());
		assertEquals(caze.getDistrict(), originalCase.getDistrict());
		assertEquals(caze.getRegion(), originalCase.getRegion());

		// make changes on original DTO and clone it. check the clone DTO has the new values
		originalCase.setDisease(Disease.DENGUE);
		originalCase.setCaseClassification(CaseClassification.CONFIRMED);
		originalCase.setDiseaseDetails(null);

		CaseDataDto cloneCase = getCaseFacade().cloneCase(originalCase);
		assertNotEquals(caze.getUuid(), cloneCase.getUuid());
		assertEquals(Disease.DENGUE, cloneCase.getDisease());
		assertEquals(CaseClassification.CONFIRMED, cloneCase.getCaseClassification());
		assertNull(cloneCase.getDiseaseDetails());
		assertEquals(caze.getPerson(), cloneCase.getPerson());
		assertEquals(caze.getDistrict(), cloneCase.getDistrict());
		assertEquals(caze.getRegion(), cloneCase.getRegion());

		// recheck values for the original DTO it has the same values.
		originalCase = getCaseFacade().getCaseDataByUuid(caze.getUuid());
		assertEquals(caze.getUuid(), originalCase.getUuid());
		assertEquals(Disease.CORONAVIRUS, originalCase.getDisease());
		assertEquals(CaseClassification.SUSPECT, originalCase.getCaseClassification());
		assertEquals(diseaseDetails, originalCase.getDiseaseDetails());
		assertEquals(caze.getPerson(), originalCase.getPerson());
		assertEquals(caze.getDistrict(), originalCase.getDistrict());
		assertEquals(caze.getRegion(), originalCase.getRegion());

	}

	@Test
	public void testDoesEpidNumberExist() {

		RDCFEntities rdcf = creator.createRDCFEntities();
		UserReferenceDto user = creator.createUser(rdcf).toReference();
		PersonReferenceDto cazePerson = creator.createPerson("Horst", "Meyer").toReference();
		CaseDataDto caze =
			creator.createCase(user, cazePerson, Disease.CHOLERA, CaseClassification.NOT_CLASSIFIED, InvestigationStatus.PENDING, new Date(), rdcf);

		// 1. Same case
		assertFalse(getCaseFacade().doesEpidNumberExist(caze.getEpidNumber(), caze.getUuid(), caze.getDisease()));

		// 2. Same disease and epid number
		assertTrue(getCaseFacade().doesEpidNumberExist(caze.getEpidNumber(), "abc", caze.getDisease()));

		// 3. Same disease, different epid number
		assertFalse(getCaseFacade().doesEpidNumberExist("123", "abc", caze.getDisease()));

		// 4. Different disease and same epid number
		assertFalse(getCaseFacade().doesEpidNumberExist(caze.getEpidNumber(), "abc", Disease.ANTHRAX));

		// 5. Different disease and different epid number
		assertFalse(getCaseFacade().doesEpidNumberExist("def", "abc", Disease.ANTHRAX));
	}

	@Test
	public void testSymptomsUpdatedByVisit() {

		RDCF rdcf = creator.createRDCF();
		UserReferenceDto user = creator.createUser(rdcf).toReference();
		PersonReferenceDto cazePerson = creator.createPerson("Foo", "Bar").toReference();
		CaseDataDto caze = creator
			.createCase(user, cazePerson, Disease.CORONAVIRUS, CaseClassification.NOT_CLASSIFIED, InvestigationStatus.PENDING, new Date(), rdcf);
		caze.getSymptoms().setChestPain(SymptomState.YES);

		// Add a new visit to the case
		VisitDto visit = creator.createVisit(caze.getDisease(), caze.getPerson(), caze.getReportDate(), VisitStatus.COOPERATIVE, VisitOrigin.USER);
		visit.getSymptoms().setAbdominalPain(SymptomState.YES);
		visit.getSymptoms().setChestPain(SymptomState.NO);

		getCaseFacade().save(caze);
		getVisitFacade().saveVisit(visit);
		CaseDataDto updatedCase = getCaseFacade().getCaseDataByUuid(caze.getUuid());

		assertEquals(SymptomState.YES, updatedCase.getSymptoms().getChestPain());
		assertEquals(SymptomState.YES, updatedCase.getSymptoms().getAbdominalPain());

		// Update an existing visit
		visit.getSymptoms().setAcuteRespiratoryDistressSyndrome(SymptomState.YES);
		getVisitFacade().saveVisit(visit);

		updatedCase = getCaseFacade().getCaseDataByUuid(caze.getUuid());

		assertEquals(SymptomState.YES, updatedCase.getSymptoms().getChestPain());
		assertEquals(SymptomState.YES, updatedCase.getSymptoms().getAbdominalPain());
		assertEquals(SymptomState.YES, updatedCase.getSymptoms().getAcuteRespiratoryDistressSyndrome());
		assertTrue(updatedCase.getSymptoms().getSymptomatic());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDoesEpidNumberExistLargeNumbers() {

		/*
		 * Running into Integer overflow is accepted since epid number follow a certain pattern
		 * and are not supposed to be bigger than Integer maxvalue.
		 */
		getCaseFacade().doesEpidNumberExist("NIE-08034912345", "not-a-uuid", Disease.OTHER);
	}

	@Test
	public void testArchiveAllArchivableCases() {

		RDCFEntities rdcf = creator.createRDCFEntities();
		UserReferenceDto user = creator.createUser(rdcf).toReference();
		PersonReferenceDto person = creator.createPerson("Walter", "Schuster").toReference();

		// One archived case
		CaseDataDto case1 = creator.createCase(user, person, rdcf);
		Case caze1 = getCaseService().getByUuid(case1.getUuid());
		getExternalShareInfoService().createAndPersistShareInfo(caze1, ExternalShareStatus.SHARED);

		CaseFacadeEjbLocal cut = getBean(CaseFacadeEjbLocal.class);
		stubFor(
			post(urlEqualTo("/export")).withRequestBody(containing(case1.getUuid()))
				.withRequestBody(containing("caseUuids"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		cut.archive(case1.getUuid(), null);
		assertTrue(cut.isArchived(case1.getUuid()));

		// One other case
		CaseDataDto case2 = creator.createCase(user, person, rdcf);
		Case caze2 = getCaseService().getByUuid(case2.getUuid());
		getExternalShareInfoService().createAndPersistShareInfo(caze2, ExternalShareStatus.SHARED);
		assertFalse(cut.isArchived(case2.getUuid()));

		stubFor(
			post(urlEqualTo("/export")).withRequestBody(containing(case2.getUuid()))
				.withRequestBody(containing("caseUuids"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK)));

		// Case of "today" shouldn't be archived
		cut.archiveAllArchivableCases(70, LocalDate.now().plusDays(69));
		assertTrue(cut.isArchived(case1.getUuid()));
		assertFalse(cut.isArchived(case2.getUuid()));

		// Case of "yesterday" should be archived
		cut.archiveAllArchivableCases(70, LocalDate.now().plusDays(71));
		assertTrue(cut.isArchived(case1.getUuid()));
		assertTrue(cut.isArchived(case2.getUuid()));
	}

	@Test
	public void testCreateInvestigationTask() {
		RDCF rdcf = creator.createRDCF();
		UserReferenceDto supervisor =
			creator.createUser(rdcf, "First", "User", creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR)).toReference();
		UserReferenceDto officer =
			creator.createUser(rdcf, "Second", "User", creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_OFFICER)).toReference();
		UserReferenceDto informant =
			creator.createUser(rdcf, "Third", "User", creator.getUserRoleReference(DefaultUserRole.COMMUNITY_INFORMANT)).toReference();
		PersonReferenceDto person = creator.createPerson("Case", "Person").toReference();

		CaseDataDto caze = creator.createCase(informant, person, rdcf);

		List<TaskDto> caseTasks = getTaskFacade().getAllPendingByCase(caze.toReference());
		assertEquals(officer, caseTasks.get(0).getAssigneeUser());
	}

	@Test
	public void testSetResponsibleSurveillanceOfficer() {
		RDCF rdcf = creator.createRDCF();
		RDCF rdcf2 = creator.createRDCF("Region2", "District2", "Community2", "Facility2");
		RDCF rdcf3 = creator.createRDCF("Region3", "District3", "Community3", "Facility3");
		creator.createUser(rdcf, "First", "User", creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_OFFICER)).toReference();
		UserReferenceDto survOff2 =
			creator.createUser(rdcf, "Second", "User", creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_OFFICER)).toReference();
		UserReferenceDto survOff3 =
			creator.createUser(rdcf2, "Third", "User", creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_OFFICER)).toReference();
		UserDto informant = creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.HOSPITAL_INFORMANT));
		informant.setAssociatedOfficer(survOff3);
		getUserFacade().saveUser(informant, false);

		// Reporting user is set as surveillance officer
		CaseDataDto caze = creator.createCase(survOff2, creator.createPerson().toReference(), rdcf);
		assertThat(caze.getSurveillanceOfficer(), is(survOff2));

		// Surveillance officer is removed if the responsible district changes
		caze.setResponsibleRegion(new RegionReferenceDto(rdcf3.region.getUuid(), null, null));
		caze.setResponsibleDistrict(new DistrictReferenceDto(rdcf3.district.getUuid(), null, null));
		caze.setResponsibleCommunity(new CommunityReferenceDto(rdcf3.community.getUuid(), null, null));
		caze.setHealthFacility(new FacilityReferenceDto(rdcf3.facility.getUuid(), null, null));
		caze = getCaseFacade().save(caze);
		assertNull(caze.getSurveillanceOfficer());

		// Surveillance officer is set to the associated officer of an informant if available
		caze.setRegion(new RegionReferenceDto(rdcf2.region.getUuid(), null, null));
		caze.setDistrict(new DistrictReferenceDto(rdcf2.district.getUuid(), null, null));
		caze.setCommunity(new CommunityReferenceDto(rdcf2.community.getUuid(), null, null));
		caze.setHealthFacility(new FacilityReferenceDto(rdcf2.facility.getUuid(), null, null));
		caze = getCaseFacade().save(caze);
		assertThat(caze.getSurveillanceOfficer(), is(survOff3));
	}

	@Test
	public void testSearchCasesFreetext() {
		RDCF rdcf = creator.createRDCF();
		final UserReferenceDto user = creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_OFFICER)).toReference();
		CaseDataDto caze = creator.createCase(user, creator.createPerson().toReference(), rdcf);
		caze.setInternalToken("internalToken");
		caze.setExternalToken("externalToken");
		caze.setExternalID("externalID");
		getCaseFacade().save(caze);

		CaseDataDto secondCaze = creator.createCase(user, creator.createPerson().toReference(), rdcf);
		secondCaze.setInternalToken("internalToken2");
		getCaseFacade().save(secondCaze);

		List<CaseIndexDto> indexList = getCaseFacade().getIndexList(new CaseCriteria(), 0, 100, Collections.emptyList());

		// test several freetext variations
		assertEquals(2, indexList.size());

		CaseCriteria caseCriteria = new CaseCriteria();

		caseCriteria.setCaseLike("internal");
		List<CaseIndexDto> indexListFiltered = getCaseFacade().getIndexList(caseCriteria, 0, 100, Collections.emptyList());
		assertEquals(2, indexListFiltered.size());

		caseCriteria.setCaseLike("Token");
		indexListFiltered = getCaseFacade().getIndexList(caseCriteria, 0, 100, Collections.emptyList());
		assertEquals(2, indexListFiltered.size());

		caseCriteria.setCaseLike("externalToken");
		indexListFiltered = getCaseFacade().getIndexList(caseCriteria, 0, 100, Collections.emptyList());
		assertEquals(1, indexListFiltered.size());
		assertThat(indexListFiltered.get(0).getUuid(), is(caze.getUuid()));

		caseCriteria.setCaseLike("externalID");
		indexListFiltered = getCaseFacade().getIndexList(caseCriteria, 0, 100, Collections.emptyList());
		assertEquals(1, indexListFiltered.size());
		assertThat(indexListFiltered.get(0).getUuid(), is(caze.getUuid()));

		caseCriteria.setCaseLike("unmatchableString");
		indexListFiltered = getCaseFacade().getIndexList(caseCriteria, 0, 100, Collections.emptyList());
		assertEquals(0, indexListFiltered.size());

		caseCriteria.setCaseLike(caze.getUuid());
		indexListFiltered = getCaseFacade().getIndexList(caseCriteria, 0, 100, Collections.emptyList());
		assertEquals(1, indexListFiltered.size());
		assertThat(indexListFiltered.get(0).getUuid(), is(caze.getUuid()));
	}

	@Test
	public void testSearchCasesWithExtendedQuarantine() {
		RDCF rdcf = creator.createRDCF();
		CaseDataDto caze = creator.createCase(
			creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_OFFICER)).toReference(),
			creator.createPerson().toReference(),
			rdcf);
		caze.setQuarantineExtended(true);
		getCaseFacade().save(caze);

		List<CaseIndexDto> indexList = getCaseFacade().getIndexList(new CaseCriteria(), 0, 100, Collections.emptyList());
		assertThat(indexList.get(0).getUuid(), is(caze.getUuid()));

		CaseCriteria caseCriteria = new CaseCriteria();
		caseCriteria.setWithExtendedQuarantine(true);

		List<CaseIndexDto> indexListFiltered = getCaseFacade().getIndexList(caseCriteria, 0, 100, Collections.emptyList());
		assertThat(indexListFiltered.get(0).getUuid(), is(caze.getUuid()));
	}

	@Test
	public void testSearchCasesWithReducedQuarantine() {
		RDCF rdcf = creator.createRDCF();
		CaseDataDto caze = creator.createCase(
			creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_OFFICER)).toReference(),
			creator.createPerson().toReference(),
			rdcf);
		caze.setQuarantineReduced(true);
		getCaseFacade().save(caze);

		List<CaseIndexDto> indexList = getCaseFacade().getIndexList(new CaseCriteria(), 0, 100, Collections.emptyList());
		assertThat(indexList.get(0).getUuid(), is(caze.getUuid()));

		CaseCriteria caseCriteria = new CaseCriteria();
		caseCriteria.setWithReducedQuarantine(true);

		List<CaseIndexDto> indexListFiltered = getCaseFacade().getIndexList(caseCriteria, 0, 100, Collections.emptyList());
		assertThat(indexListFiltered.get(0).getUuid(), is(caze.getUuid()));
	}

	@Test
	public void testGetDuplicates() {
		RDCF rdcf = creator.createRDCF();

		//case and person matching for asserts
		PersonDto person = creator.createPerson("Fname", "Lname", (p) -> {
			p.setBirthdateDD(12);
			p.setBirthdateMM(3);
			p.setBirthdateYYYY(1968);
		});

		final UserReferenceDto user = creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_OFFICER)).toReference();
		CaseDataDto caze = creator.createCase(user, rdcf, (c) -> {
			c.setPerson(person.toReference());
			c.setExternalID("test-ext-id");
			c.setExternalToken("test-ext-token");
			c.setDisease(Disease.CORONAVIRUS);
			c.setDistrict(rdcf.district);
			c.setReportDate(new Date());
		});

		// case and person matching for some asserts
		PersonDto person2 = creator.createPerson("Fname", "Lname", (p) -> {
			p.setBirthdateMM(3);
			p.setBirthdateYYYY(1968);
		});
		creator.createCase(user, rdcf, (c) -> {
			c.setPerson(person2.toReference());
			c.setDisease(Disease.CORONAVIRUS);
		});

		creator.createCase(user, rdcf, (c) -> {
			c.setPerson(creator.createPerson().toReference());
			c.setDisease(Disease.CHOLERA);
		});

		creator.createCase(user, rdcf, (c) -> {
			c.setPerson(person.toReference());
			c.setDisease(Disease.CHOLERA);
		});

		CasePersonDto casePerson = new CasePersonDto();
		PersonDto duplicatePerson = PersonDto.build();
		CaseDataDto duplicateCaze = CaseDataDto.build(duplicatePerson.toReference(), Disease.CORONAVIRUS);
		duplicateCaze.setResponsibleDistrict(rdcf.district);
		duplicateCaze.setReportDate(new Date());

		casePerson.setCaze(duplicateCaze);
		casePerson.setPerson(duplicatePerson);

		List<CasePersonDto> duplicates = getCaseFacade().getDuplicates(casePerson);
		MatcherAssert.assertThat(duplicates, hasSize(0));

		// match by external ID
		duplicateCaze.setExternalID("test-ext-id");
		duplicates = getCaseFacade().getDuplicates(casePerson);
		MatcherAssert.assertThat(duplicates, hasSize(1));

		// match by external ID case insensitive + trim
		duplicateCaze.setExternalID(" test-EXT-id ");
		duplicates = getCaseFacade().getDuplicates(casePerson);
		MatcherAssert.assertThat(duplicates, hasSize(1));

		// match by external token
		duplicateCaze.setExternalID(null);
		duplicateCaze.setExternalToken(caze.getExternalToken());
		duplicates = getCaseFacade().getDuplicates(casePerson);
		MatcherAssert.assertThat(duplicates, hasSize(1));

		// match by external token case insensitive + trim
		duplicateCaze.setExternalToken(" Test-ext-TOKEN ");
		duplicates = getCaseFacade().getDuplicates(casePerson);
		MatcherAssert.assertThat(duplicates, hasSize(1));

		// match by first name and last name
		duplicateCaze.setExternalToken(null);
		duplicatePerson.setFirstName("Fname");
		duplicatePerson.setLastName("Lname");
		duplicates = getCaseFacade().getDuplicates(casePerson);
		MatcherAssert.assertThat(duplicates, hasSize(2));

		// match by name and birth day should match also the one with missing birth day
		duplicatePerson.setBirthdateDD(12);
		duplicates = getCaseFacade().getDuplicates(casePerson);
		MatcherAssert.assertThat(duplicates, hasSize(2));

		// match by name and birth day / month should match also the one with missing birth day
		duplicatePerson.setBirthdateMM(3);
		duplicates = getCaseFacade().getDuplicates(casePerson);
		MatcherAssert.assertThat(duplicates, hasSize(2));

		// match by name and birth day / month / year should match also the one with missing birth day
		duplicatePerson.setBirthdateYYYY(1968);
		duplicates = getCaseFacade().getDuplicates(casePerson);
		MatcherAssert.assertThat(duplicates, hasSize(2));

		// match by name and birth month / year
		duplicatePerson.setBirthdateDD(null);
		duplicates = getCaseFacade().getDuplicates(casePerson);
		MatcherAssert.assertThat(duplicates, hasSize(2));

		// case insensitive name
		duplicatePerson.setFirstName(" fnamE");
		duplicatePerson.setLastName("lName ");
		duplicates = getCaseFacade().getDuplicates(casePerson);
		MatcherAssert.assertThat(duplicates, hasSize(2));
	}

	@Test
	public void testCreateCaseWithoutUuid() {
		RDCF rdcf = creator.createRDCF();
		CaseDataDto caze = new CaseDataDto();

		caze.setReportDate(new Date());
		caze.setReportingUser(creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_OFFICER)).toReference());
		caze.setCaseClassification(CaseClassification.PROBABLE);
		caze.setInvestigationStatus(InvestigationStatus.PENDING);
		caze.setDisease(Disease.CORONAVIRUS);
		caze.setPerson(creator.createPerson().toReference());
		caze.setResponsibleRegion(rdcf.region);
		caze.setResponsibleDistrict(rdcf.district);
		caze.setFacilityType(FacilityType.HOSPITAL);
		caze.setHealthFacility(rdcf.facility);

		caze.setTherapy(new TherapyDto());
		caze.setSymptoms(new SymptomsDto());
		caze.setHealthConditions(new HealthConditionsDto());
		EpiDataDto epiData = new EpiDataDto();
		ExposureDto exposure = new ExposureDto();
		exposure.setExposureType(ExposureType.WORK);
		epiData.setExposures(Collections.singletonList(exposure));
		caze.setEpiData(epiData);

		CaseDataDto savedCaze = getCaseFacade().save(caze);

		MatcherAssert.assertThat(savedCaze.getUuid(), not(isEmptyOrNullString()));
		MatcherAssert.assertThat(savedCaze.getTherapy().getUuid(), not(isEmptyOrNullString()));
		MatcherAssert.assertThat(savedCaze.getSymptoms().getUuid(), not(isEmptyOrNullString()));
		MatcherAssert.assertThat(savedCaze.getEpiData().getUuid(), not(isEmptyOrNullString()));
		MatcherAssert.assertThat(savedCaze.getEpiData().getExposures().get(0).getUuid(), not(isEmptyOrNullString()));
	}

	@Test
	public void testSearchByFacilityTypeAndGroup() {
		RDCF rdcf = creator.createRDCF();
		PersonReferenceDto personDto = creator.createPerson().toReference();
		CaseDataDto savedCaze1 = createCaseOfFacilityType(rdcf, personDto, FacilityType.HOSPITAL);
		CaseDataDto savedCaze2 = createCaseOfFacilityType(rdcf, personDto, FacilityType.MATERNITY_FACILITY);
		CaseDataDto savedCaze3 = createCaseOfFacilityType(rdcf, personDto, FacilityType.HOTEL);
		CaseDataDto savedCaze4 = createCaseOfFacilityType(rdcf, personDto, FacilityType.REFUGEE_ACCOMMODATION);

		MatcherAssert.assertThat(getCaseFacade().getIndexList(new CaseCriteria(), 0, 100, null), hasSize(4));

		final CaseCriteria facilityTypeGroupFilter = new CaseCriteria();
		facilityTypeGroupFilter.setFacilityTypeGroup(FacilityTypeGroup.MEDICAL_FACILITY);
		MatcherAssert.assertThat(getCaseFacade().getIndexList(facilityTypeGroupFilter, 0, 100, null), hasSize(2));

		final CaseCriteria facilityTypeAndGroupFilter = new CaseCriteria();
		facilityTypeAndGroupFilter.setFacilityTypeGroup(FacilityTypeGroup.MEDICAL_FACILITY);
		facilityTypeAndGroupFilter.setFacilityType(FacilityType.HOSPITAL);
		MatcherAssert.assertThat(getCaseFacade().getIndexList(facilityTypeAndGroupFilter, 0, 100, null), hasSize(1));

		final CaseCriteria facilityTypeFilter = new CaseCriteria();
		facilityTypeFilter.setFacilityType(FacilityType.HOTEL);
		final List<CaseIndexDto> indexListByFacilityTYpe = getCaseFacade().getIndexList(facilityTypeFilter, 0, 100, null);
		MatcherAssert.assertThat(indexListByFacilityTYpe, hasSize(1));
		Assert.assertEquals(indexListByFacilityTYpe.get(0).getUuid(), savedCaze3.getUuid());

		final CaseCriteria facilityTypeAndGroupNonMatchingFilter = new CaseCriteria();
		facilityTypeAndGroupNonMatchingFilter.setFacilityTypeGroup(FacilityTypeGroup.MEDICAL_FACILITY);
		facilityTypeAndGroupNonMatchingFilter.setFacilityType(FacilityType.HOTEL);
		MatcherAssert.assertThat(getCaseFacade().getIndexList(facilityTypeAndGroupNonMatchingFilter, 0, 100, null), hasSize(0));
	}

	private CaseDataDto createCaseOfFacilityType(RDCF rdcf, PersonReferenceDto personDto, FacilityType facilityType) {
		CaseDataDto caze1 = CaseDataDto.build(personDto, Disease.CORONAVIRUS);
		caze1.setReportDate(new Date());
		UserDto user = getUserFacade().getByUserName("SomeUser");
		if (user == null) {
			user = creator.createUser(rdcf, "Some", "User", creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_OFFICER));
		}
		caze1.setReportingUser(user.toReference());
		caze1.setCaseClassification(CaseClassification.PROBABLE);
		caze1.setInvestigationStatus(InvestigationStatus.PENDING);
		caze1.setDisease(Disease.CORONAVIRUS);
		caze1.setPerson(personDto);
		caze1.setResponsibleRegion(rdcf.region);
		caze1.setResponsibleDistrict(rdcf.district);
		caze1.setFacilityType(facilityType);
		caze1.setHealthFacility(rdcf.facility);
		caze1.setTherapy(new TherapyDto());
		caze1.setSymptoms(new SymptomsDto());
		EpiDataDto epiData = new EpiDataDto();
		ExposureDto exposure = new ExposureDto();
		exposure.setExposureType(ExposureType.WORK);
		epiData.setExposures(Collections.singletonList(exposure));
		caze1.setEpiData(epiData);

		return getCaseFacade().save(caze1);
	}

	@Test
	public void testGetDuplicatesWithReportDateThreshold() {

		RDCF rdcf = creator.createRDCF();
		LocalDateTime now = LocalDateTime.now();

		//case and person matching for asserts
		PersonDto person = creator.createPerson("Fname", "Lname", (p) -> {
			p.setBirthdateDD(12);
			p.setBirthdateMM(3);
			p.setBirthdateYYYY(1968);
		});

		final UserReferenceDto user = creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_OFFICER)).toReference();
		CaseDataDto caze = creator.createCase(user, rdcf, (c) -> {
			c.setPerson(person.toReference());
			c.setDisease(Disease.CORONAVIRUS);
			c.setDistrict(rdcf.district);
			c.setReportDate(new Date());
		});

		PersonDto person2 = creator.createPerson("Fname", "Lname", (p) -> {
			p.setBirthdateMM(3);
			p.setBirthdateYYYY(1968);
		});
		creator.createCase(user, rdcf, (c) -> {
			c.setPerson(person2.toReference());
			c.setDisease(Disease.CORONAVIRUS);
			c.setReportDate(UtilDate.from(now.minusDays(1)));
		});

		CasePersonDto casePerson = new CasePersonDto();
		PersonDto duplicatePerson = PersonDto.build();
		CaseDataDto duplicateCaze = CaseDataDto.build(duplicatePerson.toReference(), Disease.CORONAVIRUS);
		duplicateCaze.setResponsibleDistrict(rdcf.district);
		duplicateCaze.setReportDate(new Date());

		casePerson.setCaze(duplicateCaze);
		casePerson.setPerson(duplicatePerson);

		List<CasePersonDto> duplicates;

		duplicateCaze.setExternalToken(null);
		duplicatePerson.setFirstName("Fname");
		duplicatePerson.setLastName("Lname");
		duplicates = getCaseFacade().getDuplicates(casePerson, 1);
		MatcherAssert.assertThat(duplicates, hasSize(2));
	}

	@Test
	public void testGetCasesByPersonUuids() {

		UserReferenceDto user =
			creator.createUser(creator.createRDCFEntities(), creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR)).toReference();
		RDCF rdcf = creator.createRDCF();

		PersonReferenceDto person1 = creator.createPerson().toReference();
		CaseDataDto case1 = getCaseFacade().save(creator.createCase(user, person1, rdcf));

		PersonReferenceDto person2 = creator.createPerson().toReference();
		CaseDataDto case2 = getCaseFacade().save(creator.createCase(user, person2, rdcf));

		List<CaseDataDto> casesByPerson = getCaseFacade().getByPersonUuids(Collections.singletonList(person1.getUuid()));

		assertEquals(1, casesByPerson.size());
		assertEquals(case1.getUuid(), casesByPerson.get(0).getUuid());
		assertNotEquals(case2.getUuid(), casesByPerson.get(0).getUuid());

		casesByPerson = getCaseFacade().getByPersonUuids(Arrays.asList(person1.getUuid(), person2.getUuid()));

		assertEquals(2, casesByPerson.size());
		assertEquals(case1.getUuid(), casesByPerson.get(0).getUuid());
		assertEquals(case2.getUuid(), casesByPerson.get(1).getUuid());
	}

	@Test
	public void testUpdateFollowUpUntilAndStatus() {

		RDCFEntities rdcf = creator.createRDCFEntities("Region", "District", "Community", "Facility");
		UserDto user = creator.createUser(
			rdcf.region.getUuid(),
			rdcf.district.getUuid(),
			rdcf.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
		PersonDto cazePerson = creator.createPerson("Case", "Person");
		CaseDataDto caze = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);

		assertEquals(FollowUpStatus.FOLLOW_UP, caze.getFollowUpStatus());
		assertEquals(LocalDate.now().plusDays(21), UtilDate.toLocalDate(caze.getFollowUpUntil()));

		VisitDto visit = creator
			.createVisit(caze.getDisease(), cazePerson.toReference(), DateUtils.addDays(new Date(), 21), VisitStatus.UNAVAILABLE, VisitOrigin.USER);

		// Follow-up until should be increased by one day
		caze = getCaseFacade().getCaseDataByUuid(caze.getUuid());
		assertEquals(FollowUpStatus.FOLLOW_UP, caze.getFollowUpStatus());
		assertEquals(LocalDate.now().plusDays(21 + 1), UtilDate.toLocalDate(caze.getFollowUpUntil()));

		visit.setVisitStatus(VisitStatus.COOPERATIVE);
		visit = getVisitFacade().saveVisit(visit);

		// Follow-up until should be back at the original date and follow-up should be completed
		caze = getCaseFacade().getCaseDataByUuid(caze.getUuid());
		assertEquals(FollowUpStatus.COMPLETED, caze.getFollowUpStatus());
		assertEquals(LocalDate.now().plusDays(21), UtilDate.toLocalDate(caze.getFollowUpUntil()));

		// Manually overwrite and increase the follow-up until date
		caze.setFollowUpUntil(DateUtils.addDays(new Date(), 23));
		caze.setOverwriteFollowUpUntil(true);
		caze = getCaseFacade().save(caze);
		assertEquals(FollowUpStatus.FOLLOW_UP, caze.getFollowUpStatus());

		// Add a cooperative visit AFTER the follow-up until date; should set follow-up to completed
		visit.setVisitStatus(VisitStatus.UNAVAILABLE);
		visit.setVisitDateTime(caze.getFollowUpUntil());
		getVisitFacade().saveVisit(visit);
		caze = getCaseFacade().getCaseDataByUuid(caze.getUuid());
		assertEquals(FollowUpStatus.FOLLOW_UP, caze.getFollowUpStatus());
		creator
			.createVisit(caze.getDisease(), cazePerson.toReference(), DateUtils.addDays(new Date(), 24), VisitStatus.COOPERATIVE, VisitOrigin.USER);
		caze = getCaseFacade().getCaseDataByUuid(caze.getUuid());
		assertEquals(FollowUpStatus.COMPLETED, caze.getFollowUpStatus());

		// Increasing the onset date should extend follow-up
		caze.getSymptoms().setOnsetDate(DateHelper.addDays(caze.getSymptoms().getOnsetDate(), 10));
		caze = getCaseFacade().save(caze);
		assertEquals(FollowUpStatus.FOLLOW_UP, caze.getFollowUpStatus());
		assertEquals(LocalDate.now().plusDays(21 + 10), UtilDate.toLocalDate(caze.getFollowUpUntil()));
	}

	@Test
	public void testCaseCriteriaSharedWithReportingTool() {
		RDCF rdcf = creator.createRDCF();
		UserDto user = creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.NATIONAL_USER));

		CaseDataDto sharedCase = creator.createCase(user.toReference(), creator.createPerson().toReference(), rdcf);
		ExternalShareInfo shareInfo = new ExternalShareInfo();
		shareInfo.setCaze(getCaseService().getByUuid(sharedCase.getUuid()));
		shareInfo.setSender(getUserService().getByUuid(user.getUuid()));
		shareInfo.setStatus(ExternalShareStatus.SHARED);
		getExternalShareInfoService().ensurePersisted(shareInfo);

		CaseDataDto notSharedCase = creator.createCase(user.toReference(), creator.createPerson().toReference(), rdcf);

		CaseCriteria caseCriteriaForShared = new CaseCriteria();
		caseCriteriaForShared.setOnlyEntitiesSharedWithExternalSurvTool(true);

		List<CaseIndexDto> indexList = getCaseFacade().getIndexList(caseCriteriaForShared, 0, 100, null);
		MatcherAssert.assertThat(indexList, hasSize(1));
		MatcherAssert.assertThat(indexList.get(0).getUuid(), is(sharedCase.getUuid()));

		CaseCriteria caseCriteriaForNotShared = new CaseCriteria();
		caseCriteriaForNotShared.setOnlyEntitiesNotSharedWithExternalSurvTool(true);

		indexList = getCaseFacade().getIndexList(caseCriteriaForNotShared, 0, 100, null);
		MatcherAssert.assertThat(indexList, hasSize(1));
		MatcherAssert.assertThat(indexList.get(0).getUuid(), is(notSharedCase.getUuid()));
	}

	@Test
	public void testCaseCriteriaChangedSinceLastShareWithReportingTool() {
		RDCF rdcf = creator.createRDCF();
		UserDto user = creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.NATIONAL_USER));

		CaseDataDto sharedCase = creator.createCase(user.toReference(), creator.createPerson().toReference(), rdcf);
		ExternalShareInfo shareInfo = new ExternalShareInfo();
		shareInfo.setCreationDate(Timestamp.valueOf(LocalDateTime.of(2021, Month.APRIL, 20, 12, 31)));
		shareInfo.setCaze(getCaseService().getByUuid(sharedCase.getUuid()));
		shareInfo.setSender(getUserService().getByUuid(user.getUuid()));
		shareInfo.setStatus(ExternalShareStatus.DELETED);
		getExternalShareInfoService().ensurePersisted(shareInfo);

		sharedCase.setReInfection(YesNoUnknown.YES);
		getCaseFacade().save(sharedCase);

		creator.createCase(user.toReference(), creator.createPerson().toReference(), rdcf);
		creator.createCase(user.toReference(), creator.createPerson().toReference(), rdcf);

		CaseCriteria caseCriteriaForShared = new CaseCriteria();
		caseCriteriaForShared.setOnlyEntitiesChangedSinceLastSharedWithExternalSurvTool(true);

		List<CaseIndexDto> indexList = getCaseFacade().getIndexList(caseCriteriaForShared, 0, 100, null);
		MatcherAssert.assertThat(indexList, hasSize(1));
		MatcherAssert.assertThat(indexList.get(0).getUuid(), is(sharedCase.getUuid()));
	}

	@Test
	public void testCaseCriteriaLastShareWithReportingToolBetweenDates() {
		RDCF rdcf = creator.createRDCF();
		UserDto user = creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.NATIONAL_USER));

		CaseDataDto sharedCase = creator.createCase(user.toReference(), creator.createPerson().toReference(), rdcf);
		ExternalShareInfo shareInfoMarch = new ExternalShareInfo();
		shareInfoMarch.setCreationDate(Timestamp.valueOf(LocalDateTime.of(2021, Month.MARCH, 20, 12, 31)));
		shareInfoMarch.setCaze(getCaseService().getByUuid(sharedCase.getUuid()));
		shareInfoMarch.setSender(getUserService().getByUuid(user.getUuid()));
		shareInfoMarch.setStatus(ExternalShareStatus.SHARED);
		getExternalShareInfoService().ensurePersisted(shareInfoMarch);

		ExternalShareInfo shareInfoApril = new ExternalShareInfo();
		shareInfoApril.setCreationDate(Timestamp.valueOf(LocalDateTime.of(2021, Month.APRIL, 20, 12, 31)));
		shareInfoApril.setCaze(getCaseService().getByUuid(sharedCase.getUuid()));
		shareInfoApril.setSender(getUserService().getByUuid(user.getUuid()));
		shareInfoApril.setStatus(ExternalShareStatus.DELETED);
		getExternalShareInfoService().ensurePersisted(shareInfoApril);

		sharedCase.setReInfection(YesNoUnknown.YES);
		getCaseFacade().save(sharedCase);

		creator.createCase(user.toReference(), creator.createPerson().toReference(), rdcf);
		creator.createCase(user.toReference(), creator.createPerson().toReference(), rdcf);

		CaseCriteria caseCriteriaForShared = new CaseCriteria();
		caseCriteriaForShared.setNewCaseDateType(ExternalShareDateType.LAST_EXTERNAL_SURVEILLANCE_TOOL_SHARE);
		caseCriteriaForShared
			.setNewCaseDateFrom(Date.from(LocalDateTime.of(2021, Month.APRIL, 18, 12, 31).atZone(ZoneId.systemDefault()).toInstant()));
		caseCriteriaForShared.setNewCaseDateTo(Date.from(LocalDateTime.of(2021, Month.APRIL, 21, 12, 31).atZone(ZoneId.systemDefault()).toInstant()));

		List<CaseIndexDto> indexList = getCaseFacade().getIndexList(caseCriteriaForShared, 0, 100, null);
		MatcherAssert.assertThat(indexList, hasSize(1));
		MatcherAssert.assertThat(indexList.get(0).getUuid(), is(sharedCase.getUuid()));

		// range before last share
		caseCriteriaForShared
			.setNewCaseDateFrom(Date.from(LocalDateTime.of(2021, Month.MARCH, 10, 12, 31).atZone(ZoneId.systemDefault()).toInstant()));
		caseCriteriaForShared.setNewCaseDateTo(Date.from(LocalDateTime.of(2021, Month.APRIL, 19, 10, 31).atZone(ZoneId.systemDefault()).toInstant()));
		indexList = getCaseFacade().getIndexList(caseCriteriaForShared, 0, 100, null);
		MatcherAssert.assertThat(indexList, hasSize(0));

		// range after last share
		caseCriteriaForShared
			.setNewCaseDateFrom(Date.from(LocalDateTime.of(2021, Month.APRIL, 21, 12, 31).atZone(ZoneId.systemDefault()).toInstant()));
		caseCriteriaForShared.setNewCaseDateTo(Date.from(LocalDateTime.of(2021, Month.APRIL, 22, 10, 31).atZone(ZoneId.systemDefault()).toInstant()));
		indexList = getCaseFacade().getIndexList(caseCriteriaForShared, 0, 100, null);
		MatcherAssert.assertThat(indexList, hasSize(0));

	}

	@Test
	public void testCaseCompletenessWhenCaseFound() {
		RDCF rdcf = creator.createRDCF();
		UserDto user = creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.NATIONAL_USER));

		int casesWithNoCompletenessFound = getCaseFacade().updateCompleteness();
		MatcherAssert.assertThat(casesWithNoCompletenessFound, is(0));

		PersonDto cazePerson = creator.createPerson("Case", "Person", Sex.MALE, 1980, 1, 1);
		CaseDataDto caseNoCompleteness = creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.NOT_CLASSIFIED,
			InvestigationStatus.PENDING,
			new Date(),
			rdcf);

		PersonDto cazePerson2 = creator.createPerson("Case2", "Person2", Sex.MALE, 1981, 1, 1);
		CaseDataDto caseWithCompleteness = creator.createCase(
			user.toReference(),
			cazePerson2.toReference(),
			Disease.EVD,
			CaseClassification.NOT_CLASSIFIED,
			InvestigationStatus.PENDING,
			DateUtils.addMinutes(new Date(), -3),
			rdcf);

		SessionImpl em = (SessionImpl) getEntityManager();
		QueryImplementor query2 = em.createQuery("select c from cases c where c.uuid=:uuid");
		query2.setParameter("uuid", caseWithCompleteness.getUuid());
		Case caseWithCompletenessSingleResult = (Case) query2.getSingleResult();
		caseWithCompletenessSingleResult.setCompleteness(0.7f);
		em.save(caseWithCompletenessSingleResult);

		int changedCases = getCaseFacade().updateCompleteness();

		Case completenessUpdateResult = getCaseService().getByUuid(caseNoCompleteness.getUuid());
		Case completenessUpdateResult2 = getCaseService().getByUuid(caseWithCompleteness.getUuid());

		MatcherAssert.assertThat(completenessUpdateResult.getCompleteness(), notNullValue());
		MatcherAssert.assertThat(completenessUpdateResult.getChangeDate(), equalTo(caseNoCompleteness.getChangeDate()));
		MatcherAssert.assertThat(completenessUpdateResult2.getCompleteness(), is(0.7f));
		MatcherAssert.assertThat(changedCases, is(1));

		int changedCasesAfterUpdateCompleteness = getCaseFacade().updateCompleteness();

		MatcherAssert.assertThat(changedCasesAfterUpdateCompleteness, is(0));
	}

	@Test
	public void testStringLengthValidations() {
		RDCF rdcf = creator.createRDCF();
		UserDto user = creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.NATIONAL_USER));

		CaseDataDto caze = creator.createCase(user.toReference(), creator.createPerson().toReference(), rdcf);

		caze.setDisease(Disease.OTHER);
		caze.setDiseaseDetails(randomString(600));

		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();

		MatcherAssert.assertThat(validator.validate(caze), hasSize(1));
	}

	@Test
	public void testGetCaseMeasurePerDistrict() {
		List<DataHelper.Pair<DistrictDto, BigDecimal>> caseMeasurePerDistrict =
			getCaseFacade().getCaseMeasurePerDistrict(null, null, Disease.EVD, CaseMeasure.CASE_COUNT);
		assertTrue(caseMeasurePerDistrict.isEmpty());

		RDCF rdcf1 = creator.createRDCF("Region", "District1", "Community", "Facility");
		RDCF rdcf2 = creator.createRDCF("Region", "District2", "Community", "Facility");
		UserDto user = creator.createUser(rdcf1, creator.getUserRoleReference(DefaultUserRole.NATIONAL_USER));

		CaseDataDto caze = creator.createCase(user.toReference(), creator.createPerson("Person", "One").toReference(), rdcf1);

		caseMeasurePerDistrict = getCaseFacade().getCaseMeasurePerDistrict(null, null, Disease.EVD, CaseMeasure.CASE_COUNT);

		assertEquals(1, caseMeasurePerDistrict.size());
		DataHelper.Pair<DistrictDto, BigDecimal> districtCaseCount = caseMeasurePerDistrict.get(0);
		assertEquals(rdcf1.district.getUuid(), districtCaseCount.getElement0().getUuid());
		assertEquals(1, districtCaseCount.getElement1().intValue());

		creator.createCase(user.toReference(), creator.createPerson("Person", "Two").toReference(), rdcf1);

		caseMeasurePerDistrict = getCaseFacade().getCaseMeasurePerDistrict(null, null, Disease.EVD, CaseMeasure.CASE_COUNT);

		assertEquals(1, caseMeasurePerDistrict.size());
		districtCaseCount = caseMeasurePerDistrict.get(0);
		assertEquals(rdcf1.district.getUuid(), districtCaseCount.getElement0().getUuid());
		assertEquals(2, districtCaseCount.getElement1().intValue());

		caze.setDistrict(rdcf2.district);
		getCaseFacade().save(caze);
		creator.createCase(user.toReference(), creator.createPerson("Person", "Three").toReference(), rdcf2);

		caseMeasurePerDistrict = getCaseFacade().getCaseMeasurePerDistrict(null, null, Disease.EVD, CaseMeasure.CASE_COUNT);

		assertEquals(2, caseMeasurePerDistrict.size());

		districtCaseCount = caseMeasurePerDistrict.get(0);
		assertEquals(rdcf1.district.getUuid(), districtCaseCount.getElement0().getUuid());
		assertEquals(1, districtCaseCount.getElement1().intValue());

		districtCaseCount = caseMeasurePerDistrict.get(1);
		assertEquals(rdcf2.district.getUuid(), districtCaseCount.getElement0().getUuid());
		assertEquals(2, districtCaseCount.getElement1().intValue());
	}

	@Test
	public void testGetMostRecentPreviousCase() {

		RDCF rdcf = creator.createRDCF();
		UserDto user = creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.NATIONAL_USER));
		PersonDto person1 = creator.createPerson();
		PersonDto person2 = creator.createPerson();
		Date now = new Date();

		CaseDataDto newCase = creator.createCase(user.toReference(), person1.toReference(), rdcf, c -> c.setDisease(Disease.EVD));
		CaseDataDto previousCase1 = creator.createCase(user.toReference(), person1.toReference(), rdcf, c -> {
			c.setDisease(Disease.EVD);
			c.setReportDate(DateHelper.subtractDays(now, 1));
			c.getSymptoms().setOnsetDate(DateHelper.subtractDays(now, 7));
		});
		creator.createCase(user.toReference(), person1.toReference(), rdcf, c -> {
			c.setDisease(Disease.EVD);
			c.setReportDate(DateHelper.subtractDays(now, 3));
			c.getSymptoms().setOnsetDate(DateHelper.subtractDays(now, 9));
		});
		creator.createCase(user.toReference(), person1.toReference(), rdcf, c -> c.setDisease(Disease.EVD));
		creator.createCase(user.toReference(), person1.toReference(), rdcf, c -> {
			c.setDisease(Disease.CHOLERA);
			c.getSymptoms().setOnsetDate(DateHelper.subtractDays(now, 2));
		});
		creator.createCase(user.toReference(), person2.toReference(), rdcf, c -> {
			c.setDisease(Disease.EVD);
			c.getSymptoms().setOnsetDate(DateHelper.subtractDays(now, 2));
		});

		assertThat(
			getCaseFacade().getMostRecentPreviousCase(person1.toReference(), Disease.EVD, newCase.getReportDate()).getUuid(),
			is(previousCase1.getUuid()));
	}

	@Test
	public void testDeleteWithContacts() {
		RDCF rdcf = creator.createRDCF();
		UserDto user = creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.NATIONAL_USER));
		PersonDto person1 = creator.createPerson();
		PersonDto person2 = creator.createPerson();

		CaseDataDto caze = creator.createCase(user.toReference(), person1.toReference(), rdcf);
		ContactDto contact = creator.createContact(user.toReference(), person2.toReference(), caze);

		assertEquals(1, getCaseFacade().getAllActiveUuids().size());
		assertEquals(1, getContactFacade().getAllActiveUuids().size());

		getCaseFacade().deleteWithContacts(caze.getUuid(), new DeletionDetails(DeletionReason.OTHER_REASON, "test reason"));

		assertEquals(0, getCaseFacade().getAllActiveUuids().size());
		assertEquals(0, getContactFacade().getAllActiveUuids().size());
		assertEquals(DeletionReason.OTHER_REASON, getCaseFacade().getByUuid(caze.getUuid()).getDeletionReason());
		assertEquals("test reason", getCaseFacade().getByUuid(caze.getUuid()).getOtherDeletionReason());
	}

	@Test
	public void testUpdateFollowUpComment() {
		RDCF rdcf = creator.createRDCF();
		UserDto user = creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.NATIONAL_USER));
		PersonDto person = creator.createPerson();

		String initialComment = "comment1";

		CaseDataDto caze = creator.createCase(user.toReference(), person.toReference(), rdcf, c -> {
			c.setFollowUpComment(initialComment);
		});

		CaseDataDto resultCaseDto = getCaseFacade().getCaseDataByUuid(caze.getUuid());
		assertEquals(initialComment, resultCaseDto.getFollowUpComment());

		String updateComment = "comment2";
		caze.setFollowUpComment(updateComment);
		CaseDataDto updateCase = getCaseFacade().updateFollowUpComment(caze);

		assertEquals(updateComment, updateCase.getFollowUpComment());
		resultCaseDto = getCaseFacade().getCaseDataByUuid(caze.getUuid());
		assertEquals(updateComment, resultCaseDto.getFollowUpComment());
	}

	@Test
	public void searchCasesByPersonEmail() {
		RDCF rdcf = creator.createRDCF();
		UserDto user = creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.NATIONAL_USER));
		PersonDto personWithEmail = creator.createPerson("personWithEmail", "test");
		PersonDto personWithoutEmail = creator.createPerson("personWithoutEmail", "test");

		PersonContactDetailDto primaryEmail =
			creator.createPersonContactDetail(personWithEmail.toReference(), true, PersonContactDetailType.EMAIL, "test1@email.com");
		PersonContactDetailDto secondaryEmail =
			creator.createPersonContactDetail(personWithEmail.toReference(), false, PersonContactDetailType.EMAIL, "test2@email.com");

		personWithEmail.getPersonContactDetails().add(primaryEmail);
		personWithEmail.getPersonContactDetails().add(secondaryEmail);
		getPersonFacade().save(personWithEmail);

		CaseDataDto caze1 = creator.createCase(user.toReference(), personWithEmail.toReference(), rdcf);
		CaseDataDto caze2 = creator.createCase(user.toReference(), personWithoutEmail.toReference(), rdcf);

		CaseCriteria caseCriteria = new CaseCriteria();
		List<CaseIndexDetailedDto> caseIndexDetailedDtos = getCaseFacade().getIndexDetailedList(caseCriteria, 0, 100, null);
		assertEquals(2, caseIndexDetailedDtos.size());
		List<String> uuids = caseIndexDetailedDtos.stream().map(c -> c.getUuid()).collect(Collectors.toList());
		assertTrue(uuids.contains(caze1.getUuid()));
		assertTrue(uuids.contains(caze2.getUuid()));

		caseCriteria.setPersonLike("test1@email.com");
		caseIndexDetailedDtos = getCaseFacade().getIndexDetailedList(caseCriteria, 0, 100, null);
		assertEquals(1, caseIndexDetailedDtos.size());
		assertEquals(caze1.getUuid(), caseIndexDetailedDtos.get(0).getUuid());

		caseCriteria.setPersonLike("test2@email.com");
		caseIndexDetailedDtos = getCaseFacade().getIndexDetailedList(caseCriteria, 0, 100, null);
		assertEquals(0, caseIndexDetailedDtos.size());
	}

	@Test
	public void searchCasesByPersonPhone() {
		RDCF rdcf = creator.createRDCF();
		UserDto user = creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.NATIONAL_USER));
		PersonDto personWithPhone = creator.createPerson("personWithPhone", "test");
		PersonDto personWithoutPhone = creator.createPerson("personWithoutPhone", "test");

		PersonContactDetailDto primaryPhone =
			creator.createPersonContactDetail(personWithPhone.toReference(), true, PersonContactDetailType.PHONE, "111222333");
		PersonContactDetailDto secondaryPhone =
			creator.createPersonContactDetail(personWithoutPhone.toReference(), false, PersonContactDetailType.PHONE, "444555666");

		personWithPhone.getPersonContactDetails().add(primaryPhone);
		personWithPhone.getPersonContactDetails().add(secondaryPhone);
		getPersonFacade().save(personWithPhone);

		CaseDataDto caze1 = creator.createCase(user.toReference(), personWithPhone.toReference(), rdcf);
		CaseDataDto caze2 = creator.createCase(user.toReference(), personWithoutPhone.toReference(), rdcf);

		CaseCriteria caseCriteria = new CaseCriteria();
		List<CaseIndexDetailedDto> caseIndexDetailedDtos = getCaseFacade().getIndexDetailedList(caseCriteria, 0, 100, null);
		assertEquals(2, caseIndexDetailedDtos.size());
		List<String> uuids = caseIndexDetailedDtos.stream().map(c -> c.getUuid()).collect(Collectors.toList());
		assertTrue(uuids.contains(caze1.getUuid()));
		assertTrue(uuids.contains(caze2.getUuid()));

		caseCriteria.setPersonLike("111222333");
		caseIndexDetailedDtos = getCaseFacade().getIndexDetailedList(caseCriteria, 0, 100, null);
		assertEquals(1, caseIndexDetailedDtos.size());
		assertEquals(caze1.getUuid(), caseIndexDetailedDtos.get(0).getUuid());

		caseCriteria.setPersonLike("444555666");
		caseIndexDetailedDtos = getCaseFacade().getIndexDetailedList(caseCriteria, 0, 100, null);
		assertEquals(0, caseIndexDetailedDtos.size());
	}

	@Test
	public void searchCasesByPersonOtherDetail() {
		RDCF rdcf = creator.createRDCF();
		UserDto user = creator.createUser(rdcf, creator.getUserRoleReference(DefaultUserRole.NATIONAL_USER));
		PersonDto personWithOtherDetail = creator.createPerson("personWithOtherDetail", "test");
		PersonDto personWithoutOtherDetail = creator.createPerson("personWithoutOtherDetail", "test");

		PersonContactDetailDto primaryOtherDetail =
			creator.createPersonContactDetail(personWithOtherDetail.toReference(), true, PersonContactDetailType.OTHER, "detail1");
		PersonContactDetailDto secondaryOtherDetail =
			creator.createPersonContactDetail(personWithOtherDetail.toReference(), false, PersonContactDetailType.OTHER, "detail2");

		personWithOtherDetail.getPersonContactDetails().add(primaryOtherDetail);
		personWithOtherDetail.getPersonContactDetails().add(secondaryOtherDetail);
		getPersonFacade().save(personWithOtherDetail);

		CaseDataDto caze1 = creator.createCase(user.toReference(), personWithOtherDetail.toReference(), rdcf);
		CaseDataDto caze2 = creator.createCase(user.toReference(), personWithoutOtherDetail.toReference(), rdcf);

		CaseCriteria caseCriteria = new CaseCriteria();
		List<CaseIndexDetailedDto> caseIndexDetailedDtos = getCaseFacade().getIndexDetailedList(caseCriteria, 0, 100, null);
		assertEquals(2, caseIndexDetailedDtos.size());
		List<String> uuids = caseIndexDetailedDtos.stream().map(c -> c.getUuid()).collect(Collectors.toList());
		assertTrue(uuids.contains(caze1.getUuid()));
		assertTrue(uuids.contains(caze2.getUuid()));

		caseCriteria.setPersonLike("detail1");
		caseIndexDetailedDtos = getCaseFacade().getIndexDetailedList(caseCriteria, 0, 100, null);
		assertEquals(1, caseIndexDetailedDtos.size());
		assertEquals(caze1.getUuid(), caseIndexDetailedDtos.get(0).getUuid());

		caseCriteria.setPersonLike("detail2");
		caseIndexDetailedDtos = getCaseFacade().getIndexDetailedList(caseCriteria, 0, 100, null);
		assertEquals(0, caseIndexDetailedDtos.size());
	}

	private static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ";
	private static final SecureRandom rnd = new SecureRandom();

	private String randomString(int len) {
		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++)
			sb.append(AB.charAt(rnd.nextInt(AB.length())));
		return sb.toString();
	}

	private UserDto createUser(RDCFEntities rdcfEntities) {
		return creator.createUser(
			rdcfEntities.region.getUuid(),
			rdcfEntities.district.getUuid(),
			rdcfEntities.facility.getUuid(),
			"Surv",
			"Sup",
			creator.getUserRoleReference(DefaultUserRole.SURVEILLANCE_SUPERVISOR));
	}

	private CaseDataDto createCase(UserDto user, PersonDto cazePerson, RDCFEntities rdcfEntities) {
		return creator.createCase(
			user.toReference(),
			cazePerson.toReference(),
			Disease.EVD,
			CaseClassification.PROBABLE,
			InvestigationStatus.PENDING,
			new Date(),
			rdcfEntities);
	}

	private void configureExternalSurvToolUrlForWireMock() {
		MockProducer.getProperties().setProperty("survnet.url", String.format("http://localhost:%s", WIREMOCK_TESTING_PORT));
	}

	private void clearExternalSurvToolUrlForWireMock() {
		MockProducer.getProperties().setProperty("survnet.url", "");
	}
}
