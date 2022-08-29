/*
 * SORMAS® - Surveillance Outbreak Response Management & Analysis System
 * Copyright © 2016-2022 Helmholtz-Zentrum für Infektionsforschung GmbH (HZI)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package org.sormas.e2etests.helpers;

import io.qameta.allure.model.StepResult;
import lombok.extern.slf4j.Slf4j;
import recorders.StepsLogger;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public abstract class StepsScanHelper {

    public static void getLastLanguageSetByUser(List<StepResult> testSteps) {
        log.info("Found {} steps in this test", testSteps.size());
        List<StepResult> steps = testSteps
                .stream().filter(step -> step.getName().endsWith("language from Combobox in User settings")).collect(Collectors.toList());
        log.info("log-> found {} steps which are changing language in a test", steps.size());
        for(StepResult stepResult : steps){
            log.info("[ {} ] ==== {}", stepResult.getName(), stepResult.getStatus());
            log.info("1.This test changed language to: {}", stepResult.getParameters());
            log.info("2.This test changed language to: {}", stepResult.getParameters().toString());
        }
    }

}
