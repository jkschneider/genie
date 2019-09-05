/*
 *
 *  Copyright 2018 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package com.netflix.genie.agent.execution.statemachine.actions;

import com.netflix.genie.agent.cli.UserConsole;
import com.netflix.genie.agent.execution.ExecutionContext;
import com.netflix.genie.agent.execution.exceptions.ChangeJobStatusException;
import com.netflix.genie.agent.execution.services.AgentJobService;
import com.netflix.genie.agent.execution.services.JobProcessManager;
import com.netflix.genie.agent.execution.statemachine.Events;
import com.netflix.genie.common.dto.JobStatus;
import com.netflix.genie.common.dto.JobStatusMessages;
import lombok.extern.slf4j.Slf4j;

/**
 * Action performed when in state MONITOR_JOB.
 *
 * @author mprimi
 * @since 4.0.0
 */
@Slf4j
class MonitorJobAction extends BaseStateAction implements StateAction.MonitorJob {

    private final AgentJobService agentJobService;
    private final JobProcessManager jobProcessManager;

    MonitorJobAction(
        final ExecutionContext executionContext,
        final AgentJobService agentJobService,
        final JobProcessManager jobProcessManager
    ) {
        super(executionContext);
        this.agentJobService = agentJobService;
        this.jobProcessManager = jobProcessManager;
    }

    @Override
    protected void executePreActionValidation() {
        assertClaimedJobIdPresent();
        assertCurrentJobStatusEqual(JobStatus.RUNNING);
        assertFinalJobStatusNotPresent();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Events executeStateAction(final ExecutionContext executionContext) {
        UserConsole.getLogger().info("Monitoring job...");

        final JobStatus finalJobStatus;
        try {
            finalJobStatus = jobProcessManager.waitFor();
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for job process completion", e);
        }

        log.info("Job process completed with final status {}", finalJobStatus);

        // TODO: Likely want to clean this up as it only contains a few cases.
        //       This doesn't handle if it's killed due to timeout, log file lengths exceeded, etc
        final String finalStatusMessage;
        switch (finalJobStatus) {
            case SUCCEEDED:
                finalStatusMessage = JobStatusMessages.JOB_FINISHED_SUCCESSFULLY;
                break;
            case FAILED:
                finalStatusMessage = JobStatusMessages.JOB_FAILED;
                break;
            case KILLED:
                finalStatusMessage = JobStatusMessages.JOB_KILLED_BY_USER;
                break;
            default:
                finalStatusMessage = "Job process completed with final status " + finalJobStatus;
                break;
        }

        try {
            this.agentJobService.changeJobStatus(
                executionContext.getClaimedJobId().get(),
                JobStatus.RUNNING,
                finalJobStatus,
                finalStatusMessage
            );
            executionContext.setCurrentJobStatus(finalJobStatus);
            executionContext.setFinalJobStatus(finalJobStatus);
        } catch (ChangeJobStatusException e) {
            throw new RuntimeException("Failed to update job status", e);
        }

        return Events.MONITOR_JOB_COMPLETE;
    }

    @Override
    protected void executePostActionValidation() {
        assertFinalJobStatusPresentAndValid();
    }
}
