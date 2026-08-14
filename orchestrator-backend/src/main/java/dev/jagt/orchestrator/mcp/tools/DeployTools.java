package dev.jagt.orchestrator.mcp.tools;

import dev.jagt.orchestrator.mcp.McpToolRegistry;
import dev.jagt.orchestrator.mcp.McpTools;
import dev.jagt.orchestrator.mcp.CallerScope;
import dev.jagt.orchestrator.service.DeployService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static dev.jagt.orchestrator.mcp.tools.ToolArgs.text;

/** The two tools that write a SHARED branch, and the guard that keeps them the human's. */
@Component
@RequiredArgsConstructor
public class DeployTools implements McpTools {

    private final DeployService deploys;
    private final CallerScope callerScope;

    @Override
    public void declare(McpToolRegistry tools) {
        tools.tool("deploy_task", """
                {
                  "description": "Merge the task's branch into the project's deployBranch (config.json) and push it. On merge conflict nothing is pushed and the human resolves manually. Master-only.",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string", "description": "Task id or its short alias."}
                  },
                  "required": ["taskId"]
                }""",
                (args, caller) -> deploy(text(args, "taskId"), caller));

        tools.tool("revert_task", """
                {
                  "description": "Undo a task's deploy: revert the merge commit it created on the deployBranch and push the revert. Only for a DEPLOYED task; refuses (nothing is written) when the commit is unknown, already reverted, or the revert conflicts. Master-only.",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string", "description": "Task id or its short alias."}
                  },
                  "required": ["taskId"]
                }""",
                (args, caller) -> revert(text(args, "taskId"), caller));
    }

    private String deploy(String taskId, String callerTaskId) {
        callerScope.requireMaster(callerTaskId, "deploy_task");
        return deploys.deploy(taskId);
    }

    private String revert(String taskId, String callerTaskId) {
        callerScope.requireMaster(callerTaskId, "revert_task");
        return deploys.revert(taskId);
    }
}
