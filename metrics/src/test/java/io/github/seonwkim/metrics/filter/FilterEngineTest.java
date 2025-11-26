package io.github.seonwkim.metrics.filter;

import static org.junit.jupiter.api.Assertions.*;

import io.github.seonwkim.metrics.api.ActorContext;
import io.github.seonwkim.metrics.core.MetricsConfiguration.FilterConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for FilterEngine, particularly the glob pattern matching logic.
 */
class FilterEngineTest {

    @Test
    void testSimpleActorPathMatching() {
        FilterEngine engine = FilterEngine.builder()
                .includeActors(List.of("pekko://system/user/myactor"))
                .build();

        ActorContext matching = new ActorContext("pekko://system/user/myactor", "MyActor", null);
        ActorContext notMatching = new ActorContext("pekko://system/user/other", "Other", null);

        assertTrue(engine.matches(matching), "Exact path should match");
        assertFalse(engine.matches(notMatching), "Different path should not match");
    }

    @Test
    void testSingleStarGlobPattern() {
        FilterEngine engine = FilterEngine.builder()
                .includeActors(List.of("pekko://system/user/*"))
                .build();

        ActorContext matching1 = new ActorContext("pekko://system/user/actor1", "Actor1", null);
        ActorContext matching2 = new ActorContext("pekko://system/user/actor2", "Actor2", null);
        ActorContext notMatching = new ActorContext("pekko://system/user/foo/bar", "Bar", null);

        assertTrue(engine.matches(matching1), "Single level should match *");
        assertTrue(engine.matches(matching2), "Single level should match *");
        assertFalse(engine.matches(notMatching), "Multi-level path should not match single *");
    }

    @Test
    void testDoubleStarGlobPattern() {
        FilterEngine engine =
                FilterEngine.builder().includeActors(List.of("**/user/**")).build();

        ActorContext matching1 = new ActorContext("pekko://system/user/actor", "Actor", null);
        ActorContext matching2 = new ActorContext("pekko://system/user/foo/bar", "Bar", null);
        ActorContext matching3 = new ActorContext("pekko://other-system/user/nested/deep/actor", "Actor", null);
        ActorContext notMatching = new ActorContext("pekko://system/system/actor", "Actor", null);

        assertTrue(engine.matches(matching1), "User actor should match **/user/**");
        assertTrue(engine.matches(matching2), "Nested user actor should match **/user/**");
        assertTrue(engine.matches(matching3), "Deep nested user actor should match **/user/**");
        assertFalse(engine.matches(notMatching), "System actor should not match **/user/**");
    }

    @Test
    void testQuestionMarkGlobPattern() {
        FilterEngine engine = FilterEngine.builder()
                .includeActors(List.of("pekko://system/user/actor?"))
                .build();

        ActorContext matching1 = new ActorContext("pekko://system/user/actor1", "Actor1", null);
        ActorContext matching2 = new ActorContext("pekko://system/user/actorX", "ActorX", null);
        ActorContext notMatching1 = new ActorContext("pekko://system/user/actor", "Actor", null);
        ActorContext notMatching2 = new ActorContext("pekko://system/user/actor12", "Actor12", null);

        assertTrue(engine.matches(matching1), "actor1 should match actor?");
        assertTrue(engine.matches(matching2), "actorX should match actor?");
        assertFalse(engine.matches(notMatching1), "actor should not match actor? (missing char)");
        assertFalse(engine.matches(notMatching2), "actor12 should not match actor? (too many chars)");
    }

    @Test
    void testMixedGlobPattern() {
        FilterEngine engine = FilterEngine.builder()
                .includeActors(List.of("**/user/*/worker-?"))
                .build();

        ActorContext matching = new ActorContext("pekko://system/user/group1/worker-1", "Worker", null);
        ActorContext notMatching1 = new ActorContext("pekko://system/user/worker-1", "Worker", null);
        ActorContext notMatching2 = new ActorContext("pekko://system/user/group1/worker-12", "Worker", null);

        assertTrue(engine.matches(matching), "Should match mixed pattern");
        assertFalse(engine.matches(notMatching1), "Should not match without middle level");
        assertFalse(engine.matches(notMatching2), "Should not match with extra character");
    }

    @Test
    void testExcludePatterns() {
        FilterEngine engine = FilterEngine.builder()
                .includeActors(List.of("**/user/**"))
                .excludeActors(List.of("**/temp/**"))
                .build();

        ActorContext included = new ActorContext("pekko://system/user/actor", "Actor", null);
        ActorContext excluded = new ActorContext("pekko://system/user/temp/actor", "Actor", null);

        assertTrue(engine.matches(included), "User actor should be included");
        assertFalse(engine.matches(excluded), "Temp actor should be excluded");
    }

    @Test
    void testMultipleIncludePatterns() {
        FilterEngine engine = FilterEngine.builder()
                .includeActors(List.of("**/user/**", "**/admin/**"))
                .build();

        ActorContext userActor = new ActorContext("pekko://system/user/actor", "Actor", null);
        ActorContext adminActor = new ActorContext("pekko://system/admin/actor", "Actor", null);
        ActorContext systemActor = new ActorContext("pekko://system/system/actor", "Actor", null);

        assertTrue(engine.matches(userActor), "User actor should match");
        assertTrue(engine.matches(adminActor), "Admin actor should match");
        assertFalse(engine.matches(systemActor), "System actor should not match");
    }

    @Test
    void testEmptyIncludePatternsMatchesAll() {
        FilterEngine engine = FilterEngine.builder().includeActors(List.of()).build();

        ActorContext anyActor = new ActorContext("pekko://system/user/actor", "Actor", null);

        assertTrue(engine.matches(anyActor), "Empty include patterns should match all");
    }

    @Test
    void testExcludeWithoutIncludeMatchesAll() {
        FilterEngine engine = FilterEngine.builder()
                .includeActors(List.of())
                .excludeActors(List.of("**/temp/**"))
                .build();

        ActorContext normalActor = new ActorContext("pekko://my-system/user/actor", "Actor", null);
        ActorContext tempActor = new ActorContext("pekko://my-system/user/temp/actor", "Actor", null);

        assertTrue(engine.matches(normalActor), "Normal actor should match (no include = match all)");
        assertFalse(engine.matches(tempActor), "Temp actor should be excluded");
    }

    @Test
    void testSpecialCharactersAreEscaped() {
        // Test that regex special characters are properly escaped
        FilterEngine engine = FilterEngine.builder()
                .includeActors(List.of("pekko://system/user/actor.test"))
                .build();

        ActorContext exactMatch = new ActorContext("pekko://system/user/actor.test", "Actor", null);
        ActorContext notMatch = new ActorContext("pekko://system/user/actorXtest", "Actor", null);

        assertTrue(engine.matches(exactMatch), "Dot should be treated as literal");
        assertFalse(engine.matches(notMatch), "Dot should not act as regex wildcard");
    }

    @Test
    void testMessageFiltering() {
        FilterEngine engine = FilterEngine.builder()
                .includeMessages(List.of("com.example.*.Command"))
                .build();

        assertTrue(engine.matchesMessage("com.example.user.Command"), "Should match message pattern");
        assertFalse(engine.matchesMessage("com.example.user.Event"), "Should not match different suffix");
    }

    @Test
    void testMessageExcludeFiltering() {
        FilterEngine engine = FilterEngine.builder()
                .includeMessages(List.of("com.example.**"))
                .excludeMessages(List.of("**.Internal**"))
                .build();

        assertTrue(engine.matchesMessage("com.example.user.Command"), "Should match included message");
        assertFalse(engine.matchesMessage("com.example.InternalCommand"), "Should exclude Internal messages");
    }

    @Test
    void testDoubleStarAtBeginning() {
        FilterEngine engine =
                FilterEngine.builder().includeActors(List.of("**/actor")).build();

        ActorContext matching1 = new ActorContext("pekko://system/actor", "Actor", null);
        ActorContext matching2 = new ActorContext("pekko://system/user/actor", "Actor", null);
        ActorContext matching3 = new ActorContext("pekko://system/user/deep/nested/actor", "Actor", null);
        ActorContext notMatching = new ActorContext("pekko://system/user/actor2", "Actor", null);

        assertTrue(engine.matches(matching1), "Should match with ** at beginning (short path)");
        assertTrue(engine.matches(matching2), "Should match with ** at beginning (medium path)");
        assertTrue(engine.matches(matching3), "Should match with ** at beginning (deep path)");
        assertFalse(engine.matches(notMatching), "Should not match different ending");
    }

    @Test
    void testDoubleStarAtEnd() {
        FilterEngine engine = FilterEngine.builder()
                .includeActors(List.of("pekko://system/user/**"))
                .build();

        ActorContext matching1 = new ActorContext("pekko://system/user/actor", "Actor", null);
        ActorContext matching2 = new ActorContext("pekko://system/user/foo/bar", "Actor", null);
        ActorContext notMatching = new ActorContext("pekko://system/admin/actor", "Actor", null);

        assertTrue(engine.matches(matching1), "Should match with ** at end (single level)");
        assertTrue(engine.matches(matching2), "Should match with ** at end (multi level)");
        assertFalse(engine.matches(notMatching), "Should not match different prefix");
    }

    @Test
    void testConfigurationBuilderIntegration() {
        FilterConfig config = FilterConfig.builder()
                .includeActors("**/user/**")
                .excludeActors("**/temp/**")
                .includeMessages("com.example.**")
                .build();

        FilterEngine engine = FilterEngine.from(config);

        ActorContext userActor = new ActorContext("pekko://system/user/actor", "Actor", null);
        ActorContext tempActor = new ActorContext("pekko://system/user/temp/actor", "Actor", null);

        assertTrue(engine.matches(userActor), "Should match user actor from config");
        assertFalse(engine.matches(tempActor), "Should exclude temp actor from config");
        assertTrue(engine.matchesMessage("com.example.Command"), "Should match message from config");
    }

    @Test
    void testBracketsAreEscaped() {
        FilterEngine engine = FilterEngine.builder()
                .includeActors(List.of("pekko://system/user/actor[1]"))
                .build();

        ActorContext exactMatch = new ActorContext("pekko://system/user/actor[1]", "Actor", null);
        ActorContext notMatch = new ActorContext("pekko://system/user/actor1", "Actor", null);

        assertTrue(engine.matches(exactMatch), "Brackets should be treated as literal");
        assertFalse(engine.matches(notMatch), "Brackets should not act as regex character class");
    }

    @Test
    void testParenthesesAreEscaped() {
        FilterEngine engine = FilterEngine.builder()
                .includeActors(List.of("pekko://system/user/actor(test)"))
                .build();

        ActorContext exactMatch = new ActorContext("pekko://system/user/actor(test)", "Actor", null);

        assertTrue(engine.matches(exactMatch), "Parentheses should be treated as literal");
    }

    @Test
    void testPlusSignIsEscaped() {
        FilterEngine engine = FilterEngine.builder()
                .includeActors(List.of("pekko://system/user/actor+"))
                .build();

        ActorContext exactMatch = new ActorContext("pekko://system/user/actor+", "Actor", null);
        ActorContext notMatch = new ActorContext("pekko://system/user/actorr", "Actor", null);

        assertTrue(engine.matches(exactMatch), "Plus should be treated as literal");
        assertFalse(engine.matches(notMatch), "Plus should not act as regex quantifier");
    }

    @Test
    void testCombinedPatterns() {
        FilterEngine engine = FilterEngine.builder()
                .includeActors(List.of("**/user/**", "**/admin/**"))
                .excludeActors(List.of(
                        "**/temp/**", "**/$*" // Exclude temporary actors (with $ in path)
                        ))
                .build();

        ActorContext userActor = new ActorContext("pekko://system/user/actor", "Actor", null);
        ActorContext adminActor = new ActorContext("pekko://system/admin/manager", "Manager", null);
        ActorContext tempActor = new ActorContext("pekko://system/user/temp/worker", "Worker", null);
        ActorContext tempNameActor = new ActorContext("pekko://system/user/$a", "TempActor", null);
        ActorContext systemActor = new ActorContext("pekko://system/system/logger", "Logger", null);

        assertTrue(engine.matches(userActor), "User actor should match");
        assertTrue(engine.matches(adminActor), "Admin actor should match");
        assertFalse(engine.matches(tempActor), "Temp actor should be excluded");
        assertFalse(engine.matches(tempNameActor), "Actor with $ in path should be excluded");
        assertFalse(engine.matches(systemActor), "System actor should not match (not in include list)");
    }
}
