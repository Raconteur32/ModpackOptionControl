// WebSocket client — unidirectional server -> client, auto-reconnect (tech §6).

const handlers = {};

export function onEvent(type, fn) { handlers[type] = fn; }

export function connect() {
    const ws = new WebSocket(`ws://${location.host}/ws`);
    ws.onmessage = ({ data }) => {
        const { type } = JSON.parse(data);
        handlers[type]?.();
    };
    ws.onclose = () => setTimeout(connect, 2000);
    ws.onerror = () => ws.close();
}
