/*
 *  Copyright (c) 2026
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.edc.connector.controlplane.api.management.transferprocess;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.Constraint;
import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.policy.model.Expression;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.MultiplicityConstraint;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.Prohibition;
import org.eclipse.edc.policy.model.Rule;
import org.eclipse.edc.spi.constants.CoreConstants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PolicyEnforceabilityWarnings {

    private static final Set<String> ENFORCEABLE_PERMISSION = Set.of(
            "MembershipCredential"
    );

    private static final Set<String> ENFORCEABLE_DUTY_TRANSFER = Set.of(
            "DataAccess.limit",
            "DataAccess.purpose",
            "DataAccess.duration",
            "DataAccess.payment",
            "AgreementAccess.dataspace"
    );

    private PolicyEnforceabilityWarnings() {
    }

    static JsonArray forTransferScope(Policy policy) {
        if (policy == null) {
            return Json.createArrayBuilder().build();
        }

        var warnings = new ArrayList<Warning>();
        var seen = new HashSet<String>();

        policy.getPermissions().forEach(permission -> collectWarnings(permission, RuleType.PERMISSION, Scope.TRANSFER, warnings, seen));
        policy.getObligations().forEach(duty -> collectWarnings(duty, RuleType.DUTY, Scope.TRANSFER, warnings, seen));
        policy.getProhibitions().forEach(prohibition -> collectWarnings(prohibition, RuleType.PROHIBITION, Scope.TRANSFER, warnings, seen));

        var builder = Json.createArrayBuilder();
        warnings.forEach(w -> builder.add(Json.createObjectBuilder()
                .add("leftOperand", w.leftOperand)
                .add("ruleType", w.ruleType)
                .add("scope", w.scope)
                .add("message", w.message)));
        return builder.build();
    }

    private static void collectWarnings(Rule rule, RuleType ruleType, Scope scope, List<Warning> warnings, Set<String> seen) {
        collectWarningsFromConstraints(rule.getConstraints(), ruleType, scope, warnings, seen);

        if (rule instanceof Permission permission) {
            permission.getDuties().forEach(duty -> collectWarnings(duty, RuleType.DUTY, scope, warnings, seen));
        } else if (rule instanceof Duty duty) {
            duty.getConsequences().forEach(consequence -> collectWarnings(consequence, RuleType.DUTY, scope, warnings, seen));
        } else if (rule instanceof Prohibition prohibition) {
            prohibition.getRemedies().forEach(remedy -> collectWarnings(remedy, RuleType.DUTY, scope, warnings, seen));
        }
    }

    private static void collectWarningsFromConstraints(List<Constraint> constraints, RuleType ruleType, Scope scope, List<Warning> warnings, Set<String> seen) {
        if (constraints == null || constraints.isEmpty()) {
            return;
        }

        for (var constraint : constraints) {
            if (constraint instanceof AtomicConstraint atomic) {
                var leftOperand = normalizeLeftOperand(extractLeftOperand(atomic.getLeftExpression()));
                if (leftOperand != null && !leftOperand.isBlank() && !isEnforceable(leftOperand, ruleType, scope)) {
                    var key = scope.name() + ":" + ruleType.name() + ":" + leftOperand;
                    if (seen.add(key)) {
                        warnings.add(new Warning(leftOperand, ruleType.name().toLowerCase(), scope.name().toLowerCase(),
                                "leftOperand '" + leftOperand + "' is not bound to a function for scope '" + scope.name().toLowerCase() + "'"));
                    }
                }
            } else if (constraint instanceof MultiplicityConstraint multi) {
                collectWarningsFromConstraints(multi.getConstraints(), ruleType, scope, warnings, seen);
            }
        }
    }

    private static String extractLeftOperand(Expression expression) {
        if (expression instanceof LiteralExpression literal) {
            return literal.asString();
        }
        return expression != null ? expression.toString() : null;
    }

    private static boolean isEnforceable(String leftOperand, RuleType ruleType, Scope scope) {
        return switch (ruleType) {
            case PERMISSION -> ENFORCEABLE_PERMISSION.contains(leftOperand);
            case DUTY -> scope == Scope.TRANSFER && ENFORCEABLE_DUTY_TRANSFER.contains(leftOperand);
            case PROHIBITION -> false;
        };
    }

    private static String normalizeLeftOperand(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.startsWith(CoreConstants.EDC_NAMESPACE)) {
            return raw.substring(CoreConstants.EDC_NAMESPACE.length());
        }
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            var idx = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('#'));
            if (idx >= 0 && idx + 1 < raw.length()) {
                return raw.substring(idx + 1);
            }
        }
        if (raw.contains(":") && !raw.startsWith("http")) {
            return raw.substring(raw.indexOf(':') + 1);
        }
        return raw;
    }

    private enum RuleType {
        PERMISSION,
        DUTY,
        PROHIBITION
    }

    private enum Scope {
        TRANSFER
    }

    private static class Warning {
        private final String leftOperand;
        private final String ruleType;
        private final String scope;
        private final String message;

        private Warning(String leftOperand, String ruleType, String scope, String message) {
            this.leftOperand = leftOperand;
            this.ruleType = ruleType;
            this.scope = scope;
            this.message = message;
        }
    }
}
