@UI @Sanity @Case @CaseImportExport
Feature: Case import and export tests

  @issue=SORDEV-10042 @env_main
  Scenario: Case custom export test
    When API: I create a new person
    Then API: I check that POST call body is "OK"
    And API: I check that POST call status code is 200
    Then API: I create a new case
    Then API: I check that POST call body is "OK"
    And API: I check that POST call status code is 200
    Given I log in as a Admin User
    And I click on the Cases button from navbar
    Then I filter by CaseID on Case directory page
    And I click on the Export case button
    Then I click on the Custom Case Export button
    And I click on the New Export Configuration button in Custom Case Export popup
    Then I fill Configuration Name field with Test Configuration Name
    And I select specific data to export in Export Configuration
    When I download created custom case export file
    And I delete created custom case export file
    Then I check if downloaded data generated by custom case option is correct