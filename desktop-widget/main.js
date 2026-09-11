const { app, BrowserWindow, Tray, Menu, ipcMain, shell, globalShortcut } = require('electron');
const path = require('path');
const fs = require('fs');

let win = null;
let tray = null;
let saveTimer = null;

const DEFAULT_SIZE = { width: 340, height: 520 };

function stateFile() {
  return path.join(app.getPath('userData'), 'window-state.json');
}

function loadState() {
  try {
    const s = JSON.parse(fs.readFileSync(stateFile(), 'utf8'));
    if (typeof s.width === 'number' && typeof s.height === 'number') return s;
  } catch (_) {}
  return null;
}

// リサイズ・移動のたびに書き込むと頻繁すぎるのでまとめて保存する
function saveStateSoon() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    if (!win || win.isDestroyed()) return;
    const [width, height] = win.getSize();
    const [x, y] = win.getPosition();
    try {
      fs.writeFileSync(stateFile(), JSON.stringify({ width, height, x, y }));
    } catch (_) {}
  }, 400);
}

function createWindow() {
  const saved = loadState();

  win = new BrowserWindow({
    width: saved ? saved.width : DEFAULT_SIZE.width,
    height: saved ? saved.height : DEFAULT_SIZE.height,
    minWidth: 260,
    minHeight: 320,
    frame: false,
    resizable: true,
    alwaysOnTop: true,
    skipTaskbar: true,
    transparent: true,
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false
    }
  });

  win.loadFile('widget.html');

  if (saved && typeof saved.x === 'number' && typeof saved.y === 'number') {
    win.setPosition(saved.x, saved.y);
  } else {
    // 初回は画面右下に配置
    const { screen } = require('electron');
    const { width, height } = screen.getPrimaryDisplay().workAreaSize;
    win.setPosition(width - DEFAULT_SIZE.width - 20, height - DEFAULT_SIZE.height - 20);
  }

  win.on('resize', saveStateSoon);
  win.on('move', saveStateSoon);

  win.on('close', (e) => {
    e.preventDefault();
    win.hide();
  });
}

function createTray() {
  // Use a simple icon
  const iconPath = path.join(__dirname, 'icon.png');
  try {
    tray = new Tray(iconPath);
  } catch {
    // Fallback: create tray without custom icon
    const { nativeImage } = require('electron');
    const img = nativeImage.createFromBuffer(
      Buffer.from('iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAARElEQVQ4T2NkoBAwUqifYdAY8B8E/jMwMPxHdwGKAUiS/0E0VssYsRmA7lp0A3C6BpsXsIYBLgOweQGnF3AZMOjDAABVKRAR5PytFgAAAABJRU5ErkJggg==', 'base64')
    );
    tray = new Tray(img);
  }

  const contextMenu = Menu.buildFromTemplate([
    { label: '表示', click: () => win.show() },
    { label: '終了', click: () => { win.destroy(); app.quit(); } }
  ]);
  tray.setToolTip('NeoNoting');
  tray.setContextMenu(contextMenu);
  tray.on('click', toggleWindow);
}

function toggleWindow() {
  if (win.isVisible()) {
    win.hide();
  } else {
    win.show();
  }
}

app.whenReady().then(() => {
  createWindow();
  createTray();
  globalShortcut.register('Alt+N', toggleWindow);
});

app.on('will-quit', () => {
  globalShortcut.unregisterAll();
});

app.on('window-all-closed', () => {
  // Don't quit on window close
});

// IPC: Open URL in default browser
ipcMain.on('open-url', (event, url) => {
  shell.openExternal(url);
});

// IPC: リサイズグリップからのサイズ変更
ipcMain.on('resize-window', (event, { width, height }) => {
  if (!win || win.isDestroyed()) return;
  win.setSize(Math.max(260, Math.round(width)), Math.max(320, Math.round(height)));
});
