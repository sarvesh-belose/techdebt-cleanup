package com.enterprise.techdebt.safety;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.type.ReferenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses before/after Java with JavaParser and scores structural deltas that suggest
 * behavior changes. Test files are skipped — they're handled by {@link TestDeltaSignal}.
 *
 * Detected deltas (in approximate weight order):
 *   - method removed                                 +30
 *   - method signature changed (params/return/throws) +25
 *   - security annotation changed on class or method  +35  (@PreAuthorize, @Secured, @RolesAllowed, @Transactional, @RequestMapping…)
 *   - SQL string literal changed                       +20
 *   - control-flow shape changed (if/switch count)     +12 per affected method
 *   - return statement count delta                      +8 per affected method
 *   - other annotation changed                           +5
 *
 * Score is capped at 70 per file so a single noisy file can't blow past the threshold
 * on its own, but a handful of touched service classes will. Calibration tuned in Sprint 8.
 */
@Component
public class AstDiffSignal implements SafetySignal {

    private static final Logger log = LoggerFactory.getLogger(AstDiffSignal.class);
    private static final int PER_FILE_CAP = 70;
    private static final int TOTAL_CAP = 100;

    private static final Set<String> SECURITY_ANNOTATIONS = Set.of(
            "PreAuthorize", "PostAuthorize", "Secured", "RolesAllowed",
            "Transactional", "RequestMapping", "GetMapping", "PostMapping",
            "PutMapping", "DeleteMapping", "PatchMapping", "Async");

    private static final Pattern SQL_PATTERN = Pattern.compile(
            "(?is).*\\b(select|insert|update|delete|merge|truncate)\\b.*\\bfrom\\b.*"
            + "|(?is).*\\b(insert\\s+into|update\\s+\\w+\\s+set|delete\\s+from)\\b.*");

    @Override
    public String name() {
        return "ast-diff";
    }

    @Override
    public SignalResult evaluate(List<FileChange> changes) {
        int total = 0;
        List<String> reasons = new ArrayList<>();
        for (FileChange c : changes) {
            if (!c.isJava() || c.isTest()) continue;
            int contribution = scoreFile(c, reasons);
            total += contribution;
            if (total >= TOTAL_CAP) {
                total = TOTAL_CAP;
                break;
            }
        }
        return SignalResult.builder()
                .signalName(name())
                .score(total)
                .reasons(reasons)
                .build();
    }

    private int scoreFile(FileChange c, List<String> reasons) {
        CompilationUnit before = safeParse(c.getBeforeContent());
        CompilationUnit after = safeParse(c.getAfterContent());
        if (before == null && after == null) return 0;
        if (before == null || after == null) {
            // File added or deleted under src/main/java — treat as significant.
            reasons.add(c.getPath() + ": file " + (before == null ? "added" : "deleted"));
            return Math.min(40, PER_FILE_CAP);
        }

        int score = 0;
        List<String> local = new ArrayList<>();

        // Class-level annotations (e.g. @RestController, @Transactional)
        score += compareAnnotations(c.getPath(), "class",
                topLevelAnnotations(before), topLevelAnnotations(after), local);

        Map<String, MethodSummary> beforeMethods = methodMap(before);
        Map<String, MethodSummary> afterMethods = methodMap(after);

        for (String key : beforeMethods.keySet()) {
            if (!afterMethods.containsKey(key)) {
                score += 30;
                local.add(c.getPath() + ": method removed " + key);
            }
        }

        for (Map.Entry<String, MethodSummary> e : afterMethods.entrySet()) {
            MethodSummary aft = e.getValue();
            MethodSummary bef = beforeMethods.get(e.getKey());
            if (bef == null) continue;          // newly added methods aren't penalized here
            if (!Objects.equals(bef.signature, aft.signature)) {
                score += 25;
                local.add(c.getPath() + ": signature changed " + aft.signature);
            }
            score += compareAnnotations(c.getPath(), "method " + e.getKey(),
                    bef.annotations, aft.annotations, local);
            if (bef.ifCount != aft.ifCount || bef.switchCount != aft.switchCount) {
                score += 12;
                local.add(c.getPath() + ": control-flow shape changed in " + e.getKey()
                        + " (if " + bef.ifCount + "→" + aft.ifCount
                        + ", switch " + bef.switchCount + "→" + aft.switchCount + ")");
            }
            if (bef.returnCount != aft.returnCount) {
                score += 8;
                local.add(c.getPath() + ": return-count change in " + e.getKey()
                        + " (" + bef.returnCount + "→" + aft.returnCount + ")");
            }
            int sqlDelta = compareSqlLiterals(bef.sqlLiterals, aft.sqlLiterals);
            if (sqlDelta > 0) {
                score += sqlDelta;
                local.add(c.getPath() + ": SQL literal change in " + e.getKey());
            }
            if (score >= PER_FILE_CAP) break;
        }

        int capped = Math.min(score, PER_FILE_CAP);
        reasons.addAll(local);
        return capped;
    }

    private int compareAnnotations(String path, String scope,
                                   Set<String> before, Set<String> after, List<String> reasons) {
        int score = 0;
        Set<String> added = new HashSet<>(after);
        added.removeAll(before);
        Set<String> removed = new HashSet<>(before);
        removed.removeAll(after);
        for (String a : added) {
            int w = SECURITY_ANNOTATIONS.contains(a) ? 35 : 5;
            score += w;
            reasons.add(path + ": " + scope + " gained @" + a);
        }
        for (String a : removed) {
            int w = SECURITY_ANNOTATIONS.contains(a) ? 35 : 5;
            score += w;
            reasons.add(path + ": " + scope + " lost @" + a);
        }
        return score;
    }

    private int compareSqlLiterals(Set<String> before, Set<String> after) {
        Set<String> diff = new HashSet<>();
        diff.addAll(before);
        diff.removeAll(after);
        Set<String> diff2 = new HashSet<>(after);
        diff2.removeAll(before);
        if (diff.isEmpty() && diff2.isEmpty()) return 0;
        return 20;
    }

    private CompilationUnit safeParse(String source) {
        if (source == null || source.isBlank()) return null;
        try {
            return StaticJavaParser.parse(source);
        } catch (ParseProblemException ex) {
            log.debug("JavaParser failed: {}", ex.getMessage());
            return null;
        } catch (RuntimeException ex) {
            log.debug("Unexpected parse error: {}", ex.getMessage());
            return null;
        }
    }

    private Set<String> topLevelAnnotations(CompilationUnit cu) {
        Set<String> names = new HashSet<>();
        for (Node node : cu.getTypes()) {
            if (node instanceof ClassOrInterfaceDeclaration cls) {
                for (AnnotationExpr a : cls.getAnnotations()) {
                    names.add(a.getNameAsString());
                }
            } else if (node instanceof AnnotationDeclaration a) {
                for (AnnotationExpr ann : a.getAnnotations()) {
                    names.add(ann.getNameAsString());
                }
            }
        }
        return names;
    }

    private Map<String, MethodSummary> methodMap(CompilationUnit cu) {
        Map<String, MethodSummary> map = new HashMap<>();
        for (Node node : cu.getTypes()) {
            if (!(node instanceof ClassOrInterfaceDeclaration cls)) continue;
            for (MethodDeclaration m : cls.getMethods()) {
                String key = methodKey(cls.getNameAsString(), m);
                map.put(key, summarize(m));
            }
        }
        return map;
    }

    private String methodKey(String className, MethodDeclaration m) {
        String params = m.getParameters().stream()
                .map(this::paramType)
                .collect(Collectors.joining(","));
        return className + "#" + m.getNameAsString() + "(" + params + ")";
    }

    private String paramType(Parameter p) {
        if (p.getType() instanceof ReferenceType rt) {
            return rt.toString();
        }
        return p.getType().toString();
    }

    private MethodSummary summarize(MethodDeclaration m) {
        MethodSummary s = new MethodSummary();
        s.signature = m.getDeclarationAsString(false, false, false);
        s.annotations = m.getAnnotations().stream()
                .map(AnnotationExpr::getNameAsString)
                .collect(Collectors.toCollection(HashSet::new));
        m.findAll(IfStmt.class).forEach(i -> s.ifCount++);
        m.findAll(SwitchStmt.class).forEach(i -> s.switchCount++);
        m.findAll(ReturnStmt.class).forEach(i -> s.returnCount++);
        m.findAll(StringLiteralExpr.class).forEach(sl -> {
            String v = sl.getValue();
            if (v.length() > 8 && SQL_PATTERN.matcher(v).matches()) {
                s.sqlLiterals.add(v);
            }
        });
        return s;
    }

    private static class MethodSummary {
        String signature;
        Set<String> annotations = new HashSet<>();
        int ifCount;
        int switchCount;
        int returnCount;
        Set<String> sqlLiterals = new HashSet<>();
    }
}
