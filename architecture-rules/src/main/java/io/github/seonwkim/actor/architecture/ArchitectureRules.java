package io.github.seonwkim.actor.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.properties.HasModifiers;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The rules of ARCHITECTURE.md ("Layers inside every module"), unfrozen. Each allows an empty
 * class set: a module may have no class in a given layer (actor-autoconfigure is all autoconfigure).
 */
public final class ArchitectureRules {

    static final String INTERNAL = "io.github.seonwkim.actor..internal..";
    static final String AUTOCONFIGURE = "io.github.seonwkim.actor..autoconfigure..";
    static final String PEKKO = "org.apache.pekko..";
    private static final String PEKKO_PREFIX = "org.apache.pekko.";

    private ArchitectureRules() {}

    /** Rule 2: nothing depends on {@code autoconfigure}. */
    public static ArchRule nothingDependsOnAutoconfigure() {
        return noClasses()
                .that()
                .resideOutsideOfPackage(AUTOCONFIGURE)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(AUTOCONFIGURE)
                .because("autoconfigure is the top layer: it wires the others and nothing depends on it")
                .allowEmptyShould(true);
    }

    /** Rule 2: {@code api} and {@code spi} never depend on {@code internal}. */
    public static ArchRule apiAndSpiNeverUseInternal() {
        return noClasses()
                .that()
                .resideOutsideOfPackages(INTERNAL, AUTOCONFIGURE)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(INTERNAL)
                .because("internal has no compatibility promise, so the public API must not expose it")
                .allowEmptyShould(true);
    }

    /** Rule 3: only {@code internal} uses Pekko. */
    public static ArchRule onlyInternalUsesPekko() {
        return noClasses()
                .that()
                .resideOutsideOfPackage(INTERNAL)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(PEKKO)
                .because("Pekko is an implementation detail, adapted only in internal")
                .allowEmptyShould(true);
    }

    /** Rule 4: no public or protected signature outside {@code internal} mentions a Pekko type. */
    public static ArchRule noPekkoInPublicSignatures() {
        return classes()
                .that()
                .resideOutsideOfPackage(INTERNAL)
                .should(notExposePekkoTypes())
                .because("users compile against the starter's API, which has no Pekko types")
                .allowEmptyShould(true);
    }

    /** Rule 5: examples use only what users can use, never {@code internal}. */
    public static ArchRule examplesNeverUseInternal() {
        return noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage(INTERNAL)
                .because("examples show the public API; internal has no compatibility promise")
                .allowEmptyShould(true);
    }

    private static ArchCondition<JavaClass> notExposePekkoTypes() {
        return new ArchCondition<>("not expose Pekko types in public or protected signatures") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (!isExposed(javaClass)) {
                    return;
                }
                List<JavaType> classTypes = new ArrayList<>();
                javaClass.getSuperclass().ifPresent(classTypes::add);
                classTypes.addAll(javaClass.getInterfaces());
                javaClass.getTypeParameters().forEach(p -> classTypes.addAll(p.getBounds()));
                report(javaClass, javaClass.getName(), classTypes, events);

                for (JavaField field : javaClass.getFields()) {
                    if (isVisible(field)) {
                        report(javaClass, field.getFullName(), List.of(field.getType()), events);
                    }
                }
                for (JavaCodeUnit codeUnit : javaClass.getCodeUnits()) {
                    if (!isVisible(codeUnit) || codeUnit.getModifiers().contains(JavaModifier.BRIDGE)) {
                        continue;
                    }
                    List<JavaType> types = new ArrayList<>();
                    types.add(codeUnit.getReturnType());
                    types.addAll(codeUnit.getParameterTypes());
                    codeUnit.getThrowsClause().forEach(t -> types.add(t.getRawType()));
                    codeUnit.getTypeParameters().forEach(p -> types.addAll(p.getBounds()));
                    report(javaClass, codeUnit.getFullName(), types, events);
                }
            }
        };
    }

    private static void report(JavaClass owner, String where, List<? extends JavaType> types, ConditionEvents events) {
        types.stream()
                .flatMap(type -> type.getAllInvolvedRawTypes().stream())
                .map(JavaClass::getName)
                .filter(name -> name.startsWith(PEKKO_PREFIX))
                .distinct()
                .sorted()
                .forEach(pekkoType -> events.add(SimpleConditionEvent.violated(
                        owner, "<" + where + "> exposes Pekko type <" + pekkoType + ">")));
    }

    private static boolean isExposed(JavaClass javaClass) {
        if (!isVisible(javaClass) || javaClass.isAnonymousClass() || javaClass.isLocalClass()) {
            return false;
        }
        return javaClass.getEnclosingClass().map(ArchitectureRules::isExposed).orElse(true);
    }

    private static boolean isVisible(HasModifiers element) {
        Set<JavaModifier> modifiers = element.getModifiers();
        if (modifiers.contains(JavaModifier.SYNTHETIC)) {
            return false;
        }
        return modifiers.contains(JavaModifier.PUBLIC) || modifiers.contains(JavaModifier.PROTECTED);
    }
}
