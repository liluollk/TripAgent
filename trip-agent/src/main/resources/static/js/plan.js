/**
 * 旅行规划模块 — Agent 流水线可视化
 *
 * 使用 /api/agent/chat 接口，处理 thinking/planning/executing/result/error 事件
 * 展示 PlanningAgent 生成计划 → ExecutionAgent 执行步骤的流程
 */
const PlanModule = (() => {
    const USER_ID = 'web-user';
    let isPlanning = false;

    /**
     * 提交规划请求 — 使用 SSE 流式接口
     */
    async function submit(message) {
        if (isPlanning || !message.trim()) return;
        isPlanning = true;

        showPipeline(message);
        setPhaseStatus('planning', 'active', '生成中...');

        try {
            const response = await fetch('/api/agent/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message, userId: USER_ID })
            });

            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            let currentPlan = null;

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const events = buffer.split('\n\n');
                buffer = events.pop();

                for (const event of events) {
                    if (!event.trim()) continue;

                    let eventType = '';
                    let data = '';

                    for (const line of event.split('\n')) {
                        if (line.startsWith('event:')) eventType = line.substring(6).trim();
                        else if (line.startsWith('data:')) data = line.substring(5).trim();
                    }

                    if (eventType === 'planning' && data) {
                        try {
                            currentPlan = JSON.parse(data);
                            renderPlanning(currentPlan);
                            setPhaseStatus('planning', 'done', '已完成');
                        } catch (e) {
                            console.error('解析计划失败:', e);
                        }
                    } else if (eventType === 'thinking' && data) {
                        // ReAct 思考步骤
                        appendToExecutionBody('💭 <strong>思考:</strong> ' + escapeHtml(data));
                    } else if (eventType === 'tool_call' && data) {
                        // ReAct 工具调用
                        try {
                            const toolCall = JSON.parse(data);
                            appendToExecutionBody(`🔧 <strong>调用工具:</strong> ${escapeHtml(toolCall.toolName)}(${escapeHtml(toolCall.toolInput)})`);
                        } catch (e) {
                            appendToExecutionBody('🔧 <strong>工具调用:</strong> ' + escapeHtml(data));
                        }
                    } else if (eventType === 'tool_result' && data) {
                        // ReAct 工具结果
                        appendToExecutionBody('📊 <strong>工具结果:</strong> ' + escapeHtml(data));
                    } else if (eventType === 'executing' && data) {
                        try {
                            const stepEvent = JSON.parse(data);
                            if (stepEvent.status === 'executing') {
                                setPhaseStatus('execution', 'active', stepEvent.step);
                                appendToExecutionBody(`<div class="step-divider">⏳ <strong>开始执行:</strong> ${escapeHtml(stepEvent.step)}</div>`);
                            } else if (stepEvent.status === 'completed') {
                                appendToExecutionBody(`<div class="step-divider">✅ <strong>完成:</strong> ${escapeHtml(stepEvent.step)}</div>`);
                            }
                        } catch (e) {
                            console.error('解析执行事件失败:', e);
                        }
                    } else if (eventType === 'result' && data) {
                        setPhaseStatus('execution', 'done', '已完成');
                        renderResult(data);
                        setPhaseStatus('result', 'done', '已完成');
                    } else if (eventType === 'error' && data) {
                        const activePhase = getCurrentActivePhase();
                        setPhaseStatus(activePhase, 'error', data);
                    }
                }
            }

            // 如果流正常结束但没收到 result 事件
            if (!document.getElementById('phase-result').classList.contains('done')) {
                setPhaseStatus('result', 'done', '已完成');
            }

        } catch (err) {
            console.error('规划请求失败:', err);
            const activePhase = getCurrentActivePhase();
            setPhaseStatus(activePhase, 'error', '请求失败：' + err.message);
        } finally {
            isPlanning = false;
        }
    }

    // ========== 显示/重置 ==========

    function showPipeline(inputText) {
        document.getElementById('planInput').style.display = 'none';
        document.getElementById('agentPipeline').style.display = '';
        document.getElementById('pipelineInputText').textContent = inputText;

        ['planning', 'execution', 'result'].forEach(id => {
            const el = document.getElementById('phase-' + id);
            if (el) {
                el.className = 'pipeline-phase';
                const statusEl = document.getElementById('status-' + id);
                if (statusEl) statusEl.textContent = '等待中';
                const body = document.getElementById('body-' + id);
                if (body) {
                    body.innerHTML = '';
                    body.classList.remove('has-content');
                }
            }
        });
    }

    function resetPlan() {
        document.getElementById('planInput').style.display = '';
        document.getElementById('agentPipeline').style.display = 'none';
    }

    // ========== 阶段渲染 ==========

    function renderPlanning(plan) {
        const body = document.getElementById('body-planning');
        if (!body) return;
        body.classList.add('has-content');

        let html = '';

        if (plan.summary) {
            html += `<div style="margin-bottom:12px;font-size:14px;color:var(--text-light)">
                ${escapeHtml(plan.summary)}
            </div>`;
        }

        if (plan.estimatedBudget > 0) {
            html += `<div style="margin-bottom:12px;font-size:13px;color:var(--text-light)">
                预估总费用：<strong style="color:var(--accent)">¥${plan.estimatedBudget.toFixed(0)}</strong>
            </div>`;
        }

        if (plan.cities && plan.cities.length > 0) {
            html += `<div style="margin-bottom:12px;font-size:13px;color:var(--text-light)">
                目的地：<strong>${plan.cities.join('、')}</strong>
            </div>`;
        }

        if (plan.steps && plan.steps.length > 0) {
            html += '<div class="plan-steps">';
            plan.steps.forEach((step, i) => {
                const typeIcon = getStepIcon(step.type);
                html += `<div class="plan-step-row">
                    <div class="plan-step-num">${step.index || i + 1}</div>
                    <div class="plan-step-info">
                        <div class="plan-step-action">${typeIcon} ${escapeHtml(step.description)}</div>
                        <div class="plan-step-desc">城市: ${escapeHtml(step.city)}</div>
                        <div class="plan-step-meta">
                            <span>🔧 ${escapeHtml(step.toolName)}</span>
                        </div>
                    </div>
                </div>`;
            });
            html += '</div>';
        }

        body.innerHTML = html;
    }

    function renderResult(resultText) {
        const body = document.getElementById('body-result');
        if (!body) return;
        body.classList.add('has-content');

        // 尝试解析 JSON 格式的结果
        try {
            const result = JSON.parse(resultText);
            let html = '<div class="result-content">';

            if (result.planSummary) {
                html += `<div class="result-summary">${escapeHtml(result.planSummary)}</div>`;
            }

            if (result.steps && result.steps.length > 0) {
                html += '<div class="result-steps">';
                result.steps.forEach((step, i) => {
                    const statusIcon = step.success ? '✅' : '❌';
                    html += `<div class="result-step">
                        <div class="result-step-header">
                            <span class="result-step-icon">${statusIcon}</span>
                            <span class="result-step-title">${escapeHtml(step.title || `步骤 ${i + 1}`)}</span>
                        </div>
                        <div class="result-step-content">${escapeHtml(step.content || '')}</div>
                    </div>`;
                });
                html += '</div>';
            }

            if (result.recommendations) {
                html += `<div class="result-recommendations">
                    <h4>💡 建议</h4>
                    <p>${escapeHtml(result.recommendations)}</p>
                </div>`;
            }

            html += '</div>';
            body.innerHTML = html;
        } catch (e) {
            // 如果不是 JSON，直接显示文本
            body.innerHTML = `<div class="result-content"><pre>${escapeHtml(resultText)}</pre></div>`;
        }
    }

    // ========== 工具函数 ==========

    function setPhaseStatus(phase, status, text) {
        const el = document.getElementById('phase-' + phase);
        if (!el) return;
        el.className = 'pipeline-phase ' + status;
        const statusEl = document.getElementById('status-' + phase);
        if (statusEl) statusEl.textContent = text;
    }

    function appendToExecutionBody(html) {
        const body = document.getElementById('body-execution');
        if (!body) return;
        body.classList.add('has-content');
        const div = document.createElement('div');
        div.className = 'react-step';
        div.innerHTML = html;
        body.appendChild(div);
        // 滚动到可见
        div.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    function getCurrentActivePhase() {
        for (const p of ['planning', 'execution', 'result']) {
            const el = document.getElementById('phase-' + p);
            if (el && el.classList.contains('active')) return p;
        }
        return 'planning';
    }

    function getStepIcon(type) {
        const icons = {
            'WEATHER': '🌤️',
            'ATTRACTION': '🏛️',
            'HOTEL': '🏨',
            'RESTAURANT': '🍽️',
            'BUDGET': '💰'
        };
        return icons[type] || '📋';
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    return { submit, resetPlan };
})();
