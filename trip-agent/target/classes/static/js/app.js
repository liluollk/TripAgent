/**
 * 主逻辑 — 导航切换、全局事件
 */
(() => {
    // ========== Tab 切换 ==========
    document.querySelectorAll('.nav-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const tab = btn.dataset.tab;

            // 更新按钮状态
            document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            // 切换面板
            document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
            document.getElementById('panel-' + tab).classList.add('active');
        });
    });

    // ========== 聊天输入框自动伸缩 ==========
    const chatInput = document.getElementById('chatInput');
    chatInput.addEventListener('input', () => {
        chatInput.style.height = 'auto';
        chatInput.style.height = Math.min(chatInput.scrollHeight, 120) + 'px';
    });

    // ========== 规划输入框自动伸缩 ==========
    const planText = document.getElementById('planText');
    planText.addEventListener('input', () => {
        planText.style.height = 'auto';
        planText.style.height = Math.min(planText.scrollHeight, 120) + 'px';
    });
})();

// ========== 全局函数 ==========

function sendChat() {
    const input = document.getElementById('chatInput');
    const msg = input.value.trim();
    if (!msg) return;
    input.value = '';
    input.style.height = 'auto';
    ChatModule.send(msg);
}

function handleChatKey(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendChat();
    }
}

function quickChat(msg) {
    document.getElementById('chatInput').value = msg;
    sendChat();
}

function submitPlan() {
    const input = document.getElementById('planText');
    const msg = input.value.trim();
    if (!msg) {
        input.focus();
        input.parentElement.style.borderColor = '#E63946';
        setTimeout(() => { input.parentElement.style.borderColor = ''; }, 2000);
        return;
    }
    PlanModule.submit(msg);
}

function handlePlanKey(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        submitPlan();
    }
}

function fillPlan(text) {
    document.getElementById('planText').value = text;
}

function resetPlan() {
    PlanModule.resetPlan();
}

function clearHistory() {
    // 清除前端显示的聊天记录
    const messagesEl = document.getElementById('chatMessages');
    messagesEl.innerHTML = `
        <div class="welcome">
            <div class="welcome-icon">✈️</div>
            <h2>你好，我是 Trip Agent</h2>
            <p>你的 AI 旅行规划助手。告诉我你想去哪里，我来帮你规划！</p>
            <div class="quick-prompts">
                <button onclick="quickChat('推荐一个周末短途旅行目的地')">🌴 周末短途</button>
                <button onclick="quickChat('3天成都旅行，预算5000')">🐼 成都3日游</button>
                <button onclick="quickChat('日本东京7天行程规划')">🗼 东京7日游</button>
            </div>
        </div>
    `;

    // 重置规划面板
    const planInput = document.getElementById('planInput');
    const agentPipeline = document.getElementById('agentPipeline');
    if (planInput) planInput.style.display = '';
    if (agentPipeline) agentPipeline.style.display = 'none';
}
