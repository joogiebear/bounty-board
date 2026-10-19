const mineflayer = require('/home/debian/.local/share/mcctl/bot-host/node_modules/mineflayer')
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'dbg2', version: '1.21.11' })
const show = (i) => { const it = bot.currentWindow.slots[i]; return it ? it.name + ' x' + it.count + ' name=' + JSON.stringify(it.customName ? it.customName : (it.displayName || '')) + ' nbt=' + JSON.stringify(it.components ? it.components.map(c => c.type) : null) : 'empty' }
bot.on('message', (m) => console.log('msg ' + m.toString()))
bot.once('spawn', async () => {
  await new Promise((r) => setTimeout(r, 1500))
  bot.chat('/bounty gui')
  await new Promise((r) => setTimeout(r, 2000))
  const w = bot.currentWindow
  console.log('window', w && w.type, 'slots', w && w.slots.length)
  for (const i of [0, 1, 45, 49, 53]) console.log('slot', i, show(i))
  const lore = bot.currentWindow.slots[0] && bot.currentWindow.slots[0].components
  console.log('slot0 components', JSON.stringify(lore).slice(0, 700))
  const before = show(0)
  let result = 'confirmed'
  await Promise.race([bot.clickWindow(0, 0, 0).then(() => { result = 'confirmed' }), new Promise((r) => setTimeout(() => { result = 'not confirmed (cancelled by plugin)'; r() }, 2500))])
  console.log('click slot 0:', result, '| after:', show(0), '| cursor:', bot.inventory.cursor ? bot.inventory.cursor.name : 'none', '| same:', before === show(0))
  process.exit(0)
})
