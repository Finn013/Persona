const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('persona', {
  invoke: (ch, ...a) => ipcRenderer.invoke(ch, ...a),
  send: (ch, ...a) => ipcRenderer.send(ch, ...a),
  on: (ch, cb) => ipcRenderer.on(ch, (_e, ...a) => cb(...a))
});
