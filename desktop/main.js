// 치지직 데스크톱 앱: 서버(chzzk.tmdghks.dev) 화면을 창 하나에 띄운다.
const { app, BrowserWindow, shell, session } = require("electron");

const HOME = process.env.CHZZK_URL || "https://chzzk.tmdghks.dev/broadcasts";
const ORIGIN = new URL(HOME).origin;

function createWindow() {
  const win = new BrowserWindow({
    width: 1280, height: 800, minWidth: 380,
    backgroundColor: "#0b0e1a", title: "치지직", autoHideMenuBar: true,
  });
  win.loadURL(HOME);

  // 우리 사이트 밖 링크(결제창 제외)는 기본 브라우저로 연다
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith(ORIGIN)) return { action: "allow" };
    shell.openExternal(url);
    return { action: "deny" };
  });
}

app.whenReady().then(() => {
  // 라이브 송출용 카메라·마이크 권한은 우리 사이트에만 허용
  session.defaultSession.setPermissionRequestHandler((wc, permission, cb) => {
    cb(wc.getURL().startsWith(ORIGIN) && ["media", "fullscreen"].includes(permission));
  });
  createWindow();
  app.on("activate", () => BrowserWindow.getAllWindows().length || createWindow());
});

app.on("window-all-closed", () => process.platform === "darwin" || app.quit());
