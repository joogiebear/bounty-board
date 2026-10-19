const mineflayer = require('/home/debian/.local/share/mcctl/bot-host/node_modules/mineflayer')
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'dbg3', version: '1.21.11' })
const slot = (w, i) => { const it = w.slots[i]; return it ? it.name : 'empty' }
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
bot.once('spawn', async () => {
  await sleep(1500)
  bot.chat('/bounty gui'); await sleep(1500)
  let w = bot.currentWindow
  console.log('before click: slot0 =', slot(w, 0))
  const p = bot.clickWindow(0, 0, 0).catch(() => {}); await sleep(1500)
  w = bot.currentWindow
  console.log('1.5s after click: slot0 =', slot(w, 0), '| cursor =', bot.inventory.cursor ? bot.inventory.cursor.name : 'none')
  // shift click on own inventory area (hotbar slot of bottom inventory, raw 81), with a diamond given to us by the console is not possible here; just try
  bot.chat('/bounty gui'); await sleep(1500)   // reopen: shows the true server state
  w = bot.currentWindow
  console.log('after reopening: slot0 =', slot(w, 0), '| my items =', bot.inventory.items().map((i) => i.name).join(',') || 'none')
  process.exit(0)
})
