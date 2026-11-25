package com.murisegames.api.modules.Event.Social;

import com.murisegames.api.modules.Event.Social.SocialCaseTypes.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SocialCaseRulesTest {

    @Test
    void roleCardProjectionRestrictsPrivateFieldsByRole() {
        CaseContent full = new CaseContent(
                "Public lobby briefing",
                List.of("Protect the ledger"),
                List.of("Never reveal the vault code"),
                List.of("Asks about the vault code immediately"),
                List.of("Offers an urgent favor"),
                new JudgeRubric("Score composure and consistency", List.of("Stays in character"))
        );
        Map<String, Object> rawRubric = SocialCaseRules.rubricToMap(full.judgeRubric());
        UUID caseId = UUID.randomUUID();

        RoleCardView defender = SocialCaseRules.cardForRole(
                RoleCardType.DEFENDER, caseId, 2, "Case A", full, rawRubric);
        assertEquals("Public lobby briefing", defender.publicSetup());
        assertEquals(List.of("Protect the ledger"), defender.defenderGoals());
        assertEquals(List.of("Never reveal the vault code"), defender.defenderConstraints());
        assertNull(defender.attackerRedFlags());
        assertNull(defender.attackerBait());
        assertNull(defender.judgeRubric());

        RoleCardView attacker = SocialCaseRules.cardForRole(
                RoleCardType.ATTACKER, caseId, 2, "Case A", full, rawRubric);
        assertEquals("Public lobby briefing", attacker.publicSetup());
        assertEquals(List.of("Asks about the vault code immediately"), attacker.attackerRedFlags());
        assertEquals(List.of("Offers an urgent favor"), attacker.attackerBait());
        assertNull(attacker.defenderGoals());
        assertNull(attacker.defenderConstraints());
        assertNull(attacker.judgeRubric());

        RoleCardView audience = SocialCaseRules.cardForRole(
                RoleCardType.AUDIENCE, caseId, 2, "Case A", full, rawRubric);
        assertEquals("Public lobby briefing", audience.publicSetup());
        assertNull(audience.defenderGoals());
        assertNull(audience.attackerRedFlags());
        assertNull(audience.judgeRubric());

        RoleCardView judge = SocialCaseRules.cardForRole(
                RoleCardType.JUDGE, caseId, 2, "Case A", full, rawRubric);
        assertNotNull(judge.judgeRubric());
        assertEquals("Score composure and consistency", judge.judgeRubric().summary());
    }
}