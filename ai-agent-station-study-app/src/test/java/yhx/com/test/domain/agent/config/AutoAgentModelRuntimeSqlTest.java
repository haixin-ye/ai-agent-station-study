package yhx.com.test.domain.agent.config;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoAgentModelRuntimeSqlTest {

    private static final int MIN_MAIN_AGENT_OUTPUT_TOKENS = 8192;

    @Test
    public void initSql_allocatesEnoughMainAgentOutputBudgetForLongFinalAnswers() throws Exception {
        String sql = Files.readString(repoPath("docs/dev-ops/mysql/init/auto-agent-model-runtime.sql"), StandardCharsets.UTF_8);

        int mainAgentTokens = bindingMaxOutputTokens(sql, "MAIN_AGENT");

        Assert.assertTrue("MAIN_AGENT max_output_tokens should support long FINAL action JSON.",
                mainAgentTokens >= MIN_MAIN_AGENT_OUTPUT_TOKENS);
    }

    @Test
    public void patchSql_updatesExistingMainAgentOutputBudget() throws Exception {
        Path patch = repoPath("docs/dev-ops/mysql/patches/auto-agent-main-agent-output-budget.sql");
        Assert.assertTrue("Existing databases need an executable output-budget patch.", Files.exists(patch));

        String sql = Files.readString(patch, StandardCharsets.UTF_8);

        Assert.assertTrue("Patch should update MAIN_AGENT binding.",
                sql.contains("`node_code` = 'MAIN_AGENT'"));
        Assert.assertTrue("Patch should raise MAIN_AGENT max_output_tokens.",
                sql.contains("`max_output_tokens` = " + MIN_MAIN_AGENT_OUTPUT_TOKENS));
    }

    private int bindingMaxOutputTokens(String sql, String nodeCode) {
        Pattern pattern = Pattern.compile("\\('amr-bind-[^']+',\\s*'" + Pattern.quote(nodeCode)
                + "',\\s*'[^']+',\\s*'[^']+',\\s*'[^']+',\\s*[^,]+,\\s*(\\d+),");
        Matcher matcher = pattern.matcher(sql);
        Assert.assertTrue("Binding not found for node " + nodeCode, matcher.find());
        return Integer.parseInt(matcher.group(1));
    }

    private Path repoPath(String relativePath) {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 4; depth++) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
            if (current == null) {
                break;
            }
        }
        return Path.of(relativePath);
    }
}
