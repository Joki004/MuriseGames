package com.murisegames.api.modules.Event.Alibi;

import com.murisegames.api.modules.Event.Alibi.AlibiCaseService.CaseAccess;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseService.Level;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlibiCaseAccessTest {

    private final UUID me = UUID.randomUUID();
    private final UUID someoneElse = UUID.randomUUID();

    private AlibiCase caseWrittenBy(UUID author) {
        AlibiCase alibiCase = new AlibiCase();
        alibiCase.setId(UUID.randomUUID());
        alibiCase.setCreatedBy(author);
        return alibiCase;
    }

    @Test
    void theEventCreatorOpensEveryCaseAndAnswersRequests() {
        CaseAccess creator = new CaseAccess(Level.CREATOR, Set.of());

        assertThat(creator.mayOpen(caseWrittenBy(someoneElse), me)).isTrue();
        assertThat(creator.canApprove()).isTrue();
    }

    @Test
    void coOrganizersAndCaseWritersOpenOnlyTheirOwnCases() {
        for (Level level : new Level[]{Level.ORGANIZER, Level.CASE_WRITER}) {
            CaseAccess access = new CaseAccess(level, Set.of());

            assertThat(access.mayOpen(caseWrittenBy(me), me)).isTrue();
            assertThat(access.mayOpen(caseWrittenBy(someoneElse), me)).isFalse();
            assertThat(access.canApprove()).isFalse();
        }
    }

    @Test
    void anApprovedRequestOpensThatCaseOnly() {
        AlibiCase approved = caseWrittenBy(someoneElse);
        AlibiCase other = caseWrittenBy(someoneElse);
        CaseAccess access = new CaseAccess(Level.CASE_WRITER, Set.of(approved.getId()));

        assertThat(access.mayOpen(approved, me)).isTrue();
        assertThat(access.mayOpen(other, me)).isFalse();
    }
}
