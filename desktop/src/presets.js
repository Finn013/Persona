// Готовые «устройства». Поля заполняются согласованно.
// os: windows | mac | linux | android
const ANGLE = (v, r) => ({ gpuV: `Google Inc. (${v})`, gpuR: r });

const PRESETS = [
  { name: 'ПК Windows 11, Full HD', values: { os: 'windows', osver: '11', sw: '1920', sh: '1080', dpr: '1', cores: '12', memory: '8', touch: '0',
    ...ANGLE('NVIDIA', 'ANGLE (NVIDIA, NVIDIA GeForce RTX 3060 (0x00002504) Direct3D11 vs_5_0 ps_5_0, D3D11)') } },
  { name: 'Ноутбук Windows 10', values: { os: 'windows', osver: '10', sw: '1536', sh: '864', dpr: '1.25', cores: '8', memory: '8', touch: '0',
    ...ANGLE('Intel', 'ANGLE (Intel, Intel(R) UHD Graphics 620 (0x00005917) Direct3D11 vs_5_0 ps_5_0, D3D11)') } },
  { name: 'Ноутбук Windows 11, 2K', values: { os: 'windows', osver: '11', sw: '1707', sh: '1067', dpr: '1.5', cores: '16', memory: '8', touch: '0',
    ...ANGLE('AMD', 'ANGLE (AMD, AMD Radeon(TM) Graphics (0x00001681) Direct3D11 vs_5_0 ps_5_0, D3D11)') } },
  { name: 'MacBook Air M2', values: { os: 'mac', osver: '14', sw: '1470', sh: '956', dpr: '2', cores: '8', memory: '8', touch: '0',
    ...ANGLE('Apple', 'ANGLE (Apple, ANGLE Metal Renderer: Apple M2, Unspecified Version)') } },
  { name: 'iMac / Mac mini', values: { os: 'mac', osver: '14', sw: '2560', sh: '1440', dpr: '2', cores: '10', memory: '8', touch: '0',
    ...ANGLE('Apple', 'ANGLE (Apple, ANGLE Metal Renderer: Apple M1 Pro, Unspecified Version)') } },
  { name: 'Linux ПК', values: { os: 'linux', osver: '', sw: '1920', sh: '1080', dpr: '1', cores: '8', memory: '8', touch: '0',
    ...ANGLE('AMD', 'ANGLE (AMD, AMD Radeon RX 6600 (radeonsi navi23 LLVM 15.0.7), OpenGL 4.6)') } },
  { name: 'Телефон: Google Pixel 8', values: { os: 'android', osver: '14', model: 'Pixel 8', sw: '412', sh: '915', dpr: '2.625', cores: '9', memory: '8', touch: '5', gpuV: 'ARM', gpuR: 'Mali-G715' } },
  { name: 'Телефон: Samsung Galaxy S23', values: { os: 'android', osver: '14', model: 'SM-S911B', sw: '360', sh: '780', dpr: '3', cores: '8', memory: '8', touch: '5', gpuV: 'Qualcomm', gpuR: 'Adreno (TM) 740' } },
  { name: 'Телефон: Xiaomi Redmi Note 12', values: { os: 'android', osver: '13', model: '23021RAAEG', sw: '393', sh: '873', dpr: '2.75', cores: '8', memory: '4', touch: '5', gpuV: 'Qualcomm', gpuR: 'Adreno (TM) 610' } },
  { name: 'Телефон: Xiaomi 13', values: { os: 'android', osver: '14', model: '2211133G', sw: '393', sh: '851', dpr: '2.75', cores: '8', memory: '8', touch: '5', gpuV: 'Qualcomm', gpuR: 'Adreno (TM) 740' } }
];

// Все поля «устройства» по порядку (ключ, подпись)
const FIELDS = [
  ['os', 'Система'],
  ['osver', 'Версия системы'],
  ['model', 'Модель телефона (для Android)'],
  ['sw', 'Ширина экрана, px'],
  ['sh', 'Высота экрана, px'],
  ['dpr', 'Масштаб (devicePixelRatio)'],
  ['cores', 'Ядер процессора'],
  ['memory', 'Память, ГБ (сайтам видно до 8)'],
  ['gpuV', 'Видеокарта: производитель'],
  ['gpuR', 'Видеокарта: модель'],
  ['touch', 'Точек касания'],
  ['lang', 'Языки через запятую'],
  ['tz', 'Часовой пояс'],
  ['ua', 'User-Agent (пусто = собрать из полей)']
];

module.exports = { PRESETS, FIELDS };
