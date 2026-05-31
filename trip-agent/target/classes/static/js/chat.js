/**
 * 对话模块 — SSE 流式对话
 *
 * 使用 /api/agent/chat 接口，处理 thinking/planning/executing/result/error 事件
 */
const ChatModule = (() => {
    const USER_ID = 'web-user';
    let isStreaming = false;

    /**
     * 发送消息并流式接收回复
     */
    async function send(message) {
        if (isStreaming || !message.trim()) return;
        isStreaming = true;

        const messagesEl = document.getElementById('chatMessages');
        const welcomeEl = messagesEl.querySelector('.welcome');
        if (welcomeEl) welcomeEl.remove();

        // 添加用户消息
        appendMessage('user', message);

        // 添加助手消息占位
        const assistantEl = appendMessage('assistant', '');
        const bubbleEl = assistantEl.querySelector('.message-bubble');
        bubbleEl.innerHTML = '<div class="typing-indicator"><span></span><span></span><span></span></div>';

        const sendBtn = document.querySelector('.btn-send');
        sendBtn.disabled = true;

        try {
            const response = await fetch('/api/agent/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message, userId: USER_ID })
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            // 解析 SSE 流
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            let fullText = '';
            let started = false;

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });

                // 按 \n\n 分割 SSE 事件
                const events = buffer.split('\n\n');
                buffer = events.pop(); // 保留未完成的部分

                for (const event of events) {
                    if (!event.trim()) continue;

                    const lines = event.split('\n');
                    let eventType = '';
                    let data = '';

                    for (const line of lines) {
                        if (line.startsWith('event:')) {
                            eventType = line.substring(6).trim();
                        } else if (line.startsWith('data:')) {
                            data = line.substring(5).trim();
                        }
                    }

                    // 处理不同类型的事件
                    if (eventType === 'thinking' && data) {
                        if (!started) {
                            bubbleEl.textContent = '';
                            started = true;
                        }
                        fullText += '💭 思考: ' + data + '\n';
                        bubbleEl.textContent = fullText;
                        scrollToBottom();
                    } else if (eventType === 'tool_call' && data) {
                        if (!started) {
                            bubbleEl.textContent = '';
                            started = true;
                        }
                        try {
                            const toolCall = JSON.parse(data);
                            fullText += `🔧 调用工具: ${toolCall.toolName}(${toolCall.toolInput})\n`;
                            bubbleEl.textContent = fullText;
                        } catch (e) {
                            fullText += '🔧 工具调用: ' + data + '\n';
                            bubbleEl.textContent = fullText;
                        }
                        scrollToBottom();
                    } else if (eventType === 'tool_result' && data) {
                        if (!started) {
                            bubbleEl.textContent = '';
                            started = true;
                        }
                        fullText += '📊 工具结果: ' + data + '\n';
                        bubbleEl.textContent = fullText;
                        scrollToBottom();
                    } else if (eventType === 'planning' && data) {
                        if (!started) {
                            bubbleEl.textContent = '';
                            started = true;
                        }
                        try {
                            const plan = JSON.parse(data);
                            fullText += '\n📋 生成计划:\n';
                            if (plan.steps) {
                                plan.steps.forEach((step, i) => {
                                    fullText += `${i + 1}. ${step.description}\n`;
                                });
                            }
                            bubbleEl.textContent = fullText;
                        } catch (e) {
                            fullText += data + '\n';
                            bubbleEl.textContent = fullText;
                        }
                        scrollToBottom();
                    } else if (eventType === 'executing' && data) {
                        if (!started) {
                            bubbleEl.textContent = '';
                            started = true;
                        }
                        try {
                            const stepEvent = JSON.parse(data);
                            const status = stepEvent.status === 'executing' ? '⏳' : '✅';
                            fullText += `${status} ${stepEvent.step}\n`;
                            bubbleEl.textContent = fullText;
                        } catch (e) {
                            fullText += data + '\n';
                            bubbleEl.textContent = fullText;
                        }
                        scrollToBottom();
                    } else if (eventType === 'result' && data) {
                        if (!started) {
                            bubbleEl.textContent = '';
                            started = true;
                        }
                        fullText += '\n🎯 最终结果:\n' + data;
                        bubbleEl.textContent = fullText;
                        scrollToBottom();
                    } else if (eventType === 'error' && data) {
                        if (!started) {
                            bubbleEl.textContent = '';
                            started = true;
                        }
                        fullText += '\n❌ 错误: ' + data;
                        bubbleEl.textContent = fullText;
                        scrollToBottom();
                    }
                }
            }

            // 如果流结束但没有收到任何事件，显示提示
            if (!started) {
                bubbleEl.textContent = '(无回复)';
            }

        } catch (err) {
            console.error('聊天请求失败:', err);
            bubbleEl.textContent = '抱歉，请求失败：' + err.message;
        } finally {
            isStreaming = false;
            sendBtn.disabled = false;
            scrollToBottom();
        }
    }

    /**
     * 添加消息到聊天区
     */
    function appendMessage(role, text) {
        const messagesEl = document.getElementById('chatMessages');
        const div = document.createElement('div');
        div.className = `message ${role}`;
        div.innerHTML = `
            <div class="message-avatar">${role === 'user' ? '🧑' : '🤖'}</div>
            <div class="message-bubble">${escapeHtml(text)}</div>
        `;
        messagesEl.appendChild(div);
        scrollToBottom();
        return div;
    }

    function scrollToBottom() {
        const el = document.getElementById('chatMessages');
        el.scrollTop = el.scrollHeight;
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    return { send };
})();
