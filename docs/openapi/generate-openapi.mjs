/**
 * 按 zhibo-shared/docs/API约定.md 生成 OpenAPI 3.0.3
 * 用法：node generate-openapi.mjs
 *
 * D65：改接口时须同步改本脚本（与 API约定 + 代码一致）；平时禁止跑导出、禁止手改 *.openapi.yaml/json。
 * 仅用户明确要求「重新生成 / 导出 OpenAPI」时：先删旧产物再跑本脚本；不要在旧 yaml 上打补丁。
 *
 * 文案对外可读（D58，红线）：summary / description / 字段 description 写给 Apifox 读者与
 * 用文档（含 AI）反向实现服务端的人，不是本仓 agent 备忘。
 * 必须：完整句子说清做什么、允许/禁止取值、副作用（写哪些表/字段）、错误条件；
 *       展示类文案写字面示例；时间字段写含义+格式。
 * 禁止：正文堆「对齐 XX」「拒收 paid」无解释、「D100：long→days…」工单缩写、
 *       只写决策号不写行为、只写「见 API约定」却无要点。决策号最多句末括号。
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

function yamlScalar(value) {
  if (value === null || value === undefined) return 'null'
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  if (typeof value === 'number') return String(value)
  if (typeof value !== 'string') return String(value)
  if (
    value === '' ||
    /[:#{}[\],&*?|<>=!%@`']/.test(value) ||
    /^(true|false|null|yes|no|on|off)$/i.test(value) ||
    /^\d/.test(value) ||
    value.includes('"') ||
    value.includes('\\') ||
    /^\s|\s$/.test(value) ||
    value.includes('\n')
  ) {
    if (value.includes('\n')) {
      return null // signal block scalar
    }
    return JSON.stringify(value)
  }
  return value
}

function yamlLines(value, indent = 0) {
  const pad = '  '.repeat(indent)
  if (value === null || value === undefined) return [pad + 'null']
  if (typeof value === 'boolean' || typeof value === 'number') {
    return [pad + yamlScalar(value)]
  }
  if (typeof value === 'string') {
    if (value.includes('\n')) {
      const lines = value.replace(/\r\n/g, '\n').split('\n')
      return [pad + '|', ...lines.map((l) => pad + '  ' + l)]
    }
    return [pad + yamlScalar(value)]
  }
  if (Array.isArray(value)) {
    if (value.length === 0) return [pad + '[]']
    const out = []
    for (const item of value) {
      if (item !== null && typeof item === 'object' && !Array.isArray(item)) {
        const keys = Object.keys(item)
        if (keys.length === 0) {
          out.push(pad + '- {}')
          continue
        }
        const [fk, ...rest] = keys
        const firstVal = item[fk]
        if (firstVal !== null && typeof firstVal === 'object') {
          out.push(pad + '- ' + fk + ':')
          for (const line of Array.isArray(firstVal)
            ? yamlLines(firstVal, indent + 2)
            : objectBodyLines(firstVal, indent + 2)) {
            out.push(line)
          }
        } else {
          const sc = yamlScalar(firstVal)
          if (sc === null) {
            out.push(pad + '- ' + fk + ': |')
            for (const l of String(firstVal).replace(/\r\n/g, '\n').split('\n')) {
              out.push(pad + '    ' + l)
            }
          } else {
            out.push(pad + '- ' + fk + ': ' + sc)
          }
        }
        for (const k of rest) {
          const v = item[k]
          if (v !== null && typeof v === 'object') {
            out.push(pad + '  ' + k + ':')
            for (const line of Array.isArray(v)
              ? yamlLines(v, indent + 2)
              : objectBodyLines(v, indent + 2)) {
              out.push(line)
            }
          } else {
            const sc = yamlScalar(v)
            if (sc === null) {
              out.push(pad + '  ' + k + ': |')
              for (const l of String(v).replace(/\r\n/g, '\n').split('\n')) {
                out.push(pad + '    ' + l)
              }
            } else {
              out.push(pad + '  ' + k + ': ' + sc)
            }
          }
        }
      } else if (Array.isArray(item)) {
        out.push(pad + '-')
        for (const line of yamlLines(item, indent + 1)) out.push(line)
      } else {
        out.push(pad + '- ' + yamlScalar(item))
      }
    }
    return out
  }
  if (typeof value === 'object') {
    return objectBodyLines(value, indent)
  }
  return [pad + String(value)]
}

function objectBodyLines(obj, indent) {
  const pad = '  '.repeat(indent)
  const keys = Object.keys(obj)
  if (keys.length === 0) return [pad + '{}']
  const out = []
  for (const k of keys) {
    const v = obj[k]
    if (v !== null && typeof v === 'object') {
      const nested = Array.isArray(v)
        ? yamlLines(v, indent + 1)
        : objectBodyLines(v, indent + 1)
      if (
        Array.isArray(v) &&
        v.length === 0
      ) {
        out.push(pad + k + ': []')
      } else if (
        !Array.isArray(v) &&
        Object.keys(v).length === 0
      ) {
        out.push(pad + k + ': {}')
      } else {
        out.push(pad + k + ':')
        out.push(...nested)
      }
    } else {
      const sc = yamlScalar(v)
      if (sc === null) {
        out.push(pad + k + ': |')
        for (const l of String(v).replace(/\r\n/g, '\n').split('\n')) {
          out.push(pad + '  ' + l)
        }
      } else {
        out.push(pad + k + ': ' + sc)
      }
    }
  }
  return out
}

function toYaml(doc) {
  const order = [
    'openapi',
    'info',
    'servers',
    'tags',
    'paths',
    'components',
    'security',
  ]
  const keys = [
    ...order.filter((k) => k in doc),
    ...Object.keys(doc).filter((k) => !order.includes(k)),
  ]
  const out = []
  for (const key of keys) {
    const v = doc[key]
    if (v !== null && typeof v === 'object') {
      out.push(key + ':')
      out.push(
        ...(Array.isArray(v) ? yamlLines(v, 1) : objectBodyLines(v, 1)),
      )
    } else {
      out.push(key + ': ' + yamlScalar(v))
    }
  }
  return out.join('\n') + '\n'
}

const ok200 = (schemaRef, examples) => {
  const content = {
    'application/json': {
      schema: { $ref: schemaRef },
    },
  }
  if (examples) content['application/json'].examples = examples
  return {
    '200': {
      description: '业务结果（看 body.code；成功 code===0）',
      content,
    },
  }
}

const csvOk200 = (description) => ({
  '200': {
    description,
    content: {
      'text/csv': { schema: { type: 'string', format: 'binary' } },
    },
  },
})

const plainTextSuccess200 = (description) => ({
  '200': {
    description: description || '校签通过时纯文本 success（与微吼约定一致）',
    content: {
      'text/plain': { schema: { type: 'string', example: 'success' } },
    },
  },
})

const BUSINESS_UUID = {
  type: 'string',
  maxLength: 32,
  pattern: "^[0-9a-f]{32}$",
  description:
    '本站业务 UUID（32 位小写十六进制无横线，D74）；库：VARCHAR(32)',
}

const pageParams = [
  {
    name: 'page',
    in: 'query',
    schema: { type: 'integer', default: 1, minimum: 1 },
    description: '页码，从 1 起（D37）；默认 1',
  },
  {
    name: 'pageSize',
    in: 'query',
    schema: { type: 'integer', default: 20, minimum: 1, maximum: 100 },
    description: '每页条数；默认 20，最大 100',
  },
]

const bearer = [{ BearerAuth: [] }]

function wrapData(dataSchema) {
  return {
    allOf: [
      { $ref: '#/components/schemas/ApiResponseBase' },
      {
        type: 'object',
        properties: {
          data: {
            description: '业务数据；失败时可能省略',
            ...(typeof dataSchema === 'object' && dataSchema !== null
              ? dataSchema
              : { type: 'object' }),
          },
        },
      },
    ],
  }
}

/**
 * D58：每个 schema 属性必须有中文 description。
 * D61：尽量补 maxLength/format 等约束，并在 description 附带「库：TYPE」。
 * 生成时对缺注释字段按「字段名默认文案 + 路径特例」补齐；补不齐则抛错，避免再漏进 Apifox。
 */
const FIELD_DESC_BY_NAME = {
  code: '业务码：0 成功；400/401/403/500/501/502…',
  msg: '提示文案；成功一般为 ok',
  id: '主键 id',
  title: '标题',
  name: '名称',
  url: 'URL 地址',
  content: '正文内容',
  author: '作者',
  introduction: '简介',
  detailHtml: '富文本详情 HTML',
  coverUrl: '封面图 URL',
  coverObjectKey: '封面对象存储 key',
  coverFileSize: '封面文件大小（字节）',
  coverMimeType: '封面 MIME 类型',
  coverMaterialId: '封面素材 UUID（素材中心）',
  thumbUrl: '缩略图 URL',
  objectKey: '对象存储 key',
  mimeType: 'MIME 类型',
  materialName: '素材名称',
  meta: '扩展元信息（如宽高、时长文案）',
  fileSize: '文件大小（字节）',
  status: '状态',
  type: '类型',
  state: '状态',
  page: '页码，从 1 开始（D37）',
  pageSize: '每页条数',
  total: '总条数',
  list: '列表数据',
  items: '条目列表',
  children: '子节点列表',
  owners: '归属人列表',
  products: '关联商品列表',
  hosts: '主播列表',
  coursewares: '课件列表',
  materials: '课件/素材列表',
  attachments: '附件列表',
  planTimes: '计划时间段列表',
  members: '成员列表',
  menus: '菜单树/菜单项',
  menuCodes: '本站菜单 code 列表',
  points: '时序点列表',
  groupId: '分组 UUID',
  reportId: '直播报备 UUID',
  providerRoomId: '第三方房间号（如微吼 webinar id）',
  hostUrl: '角色参会 iframe 地址（微吼 page_url）；本站开播页 iframe 的 src',
  headimgurl: '员工微信头像（实时查 sys_useraccount.headimgurl，不落成员表）；空则不返回',
  embedUrl: '嵌入页 URL',
  pageUrl: '页面 URL',
  startTime: '开始时间，yyyy-MM-dd HH:mm:ss',
  endTime: '结束时间，yyyy-MM-dd HH:mm:ss',
  createdTime: '创建时间，yyyy-MM-dd HH:mm:ss',
  updatedTime: '最近更新时间，yyyy-MM-dd HH:mm:ss',
  submittedTime: '提交时间，yyyy-MM-dd HH:mm:ss',
  uploadedTime: '上传时间，yyyy-MM-dd HH:mm:ss',
  listedTime:
    '上架时间，yyyy-MM-dd HH:mm:ss；创建/保存勾选上架或列表手动上架时写入；下架/驳回一般不清；无则不返回',
  enterTime: '进入时间，yyyy-MM-dd HH:mm:ss',
  leaveTime: '离开时间，yyyy-MM-dd HH:mm:ss',
  expireTime: '有效期，yyyy-MM-dd HH:mm:ss',
  time: '时间点',
  duration: '时长',
  nickname: '昵称',
  userName: '姓名',
  userAccount: '登录账号',
  avatar: '头像 URL',
  avatarUrl: '头像 URL',
  departmentName: '部门展示名',
  roleNames: '角色名称（多角色顿号连接）',
  remark: '备注',
  reason: '原因/驳回理由',
  result: '审核结果',
  action: '操作类型',
  password: '密码',
  captchaId: '验证码 id',
  captchaCode: '图形验证码内容',
  accessToken: 'access JWT',
  refreshToken: 'refresh JWT',
  expiresIn: 'access 有效秒数',
  refreshExpiresIn: 'refresh 有效秒数',
  errorMsg: '错误说明',
  user: '当前员工信息',
  host: '主播信息',
  sdk: '观看 JS SDK 初始化载荷',
  region: '云厂商 region',
  endpoint: '对象存储 endpoint',
  bucketName: '桶名',
  accessKeyId: '临时 AccessKeyId',
  secretAccessKey: '临时 SecretAccessKey',
  sessionToken: '临时 SessionToken',
  count: '数量',
  onlineCount: '当前在线人数',
  pv: '累计观看 PV',
  online: '在线人数（或是否在线）',
  enter: '进场人数',
  leave: '离场人数',
  bucketSeconds: '时间桶秒数',
  activityState: '活动状态',
  boundLiveCount: '已绑定直播场次数',
  canBroadcast: '当前是否允许开播',
  canList: '当前是否允许上架',
  listed: '是否上架（创建/保存立刻改 shelfStatus）',
  sellAlone: '是否单独售卖',
  price: '售价（元）',
  linePrice: '划线价（元）',
  validityLabel:
    '售卖有效期展示文案；PUT …/sales 按 accessValidityType 重算写入。取值/示例：免费或 long →「长期有效」；days →「{N}天有效」（如「7天有效」「30天有效」「360天有效」）；课程另支持 fixed →「{yyyy-MM-dd HH:mm:ss}到期」（如「2026-11-14 00:00:00到期」；直播无 fixed）。空/缺省按「长期有效」。管理端列表售卖标签由前端拼「{售卖中文} / {validityLabel}」',
  playbackEnabled: '是否开启回放',
  playbackValidityDays: '回放有效天数',
  playbackExpired: '是否已超过回放有效期',
  playbackSpeedAllow: '是否允许倍速',
  playbackFastForwardAllow: '是否允许快进',
  accessExpired: '访问是否已过期',
  accessState: '观看售卖门禁状态',
  accessValidityDays: '购买后有效天数',
  account: '第三方用户唯一标识（观众=数字 viewerId）；本站回显；init 以 email 为准；聊天回包未必带回',
  ultraLowLatency:
    '是否超低延迟。true 时创建微吼传 no_delay_webinar=1，false 传 0；不传则不带该参。创建后不可改',
  verify: '观看验证方式（微吼 verify）',
  isDefault: '是否默认分组',
  liveEnabled: '本站直播账号是否启用',
  enabled: '是否启用',
  hqStatus: '总部账号状态',
  selectableOnly: '是否仅可选（两审通过）',
  productName: '商品名称',
  spuId: '订单中心商品 id（十进制字符串，D56 例外）',
  userId: '用户标识（第三方观众 id，非员工主键）',
  guestId: '本站生成的游客 id',
  mid: '幂等/消息 id',
  webinarId: '微吼活动号',
  appKey: '微吼 app_key',
  signedAt: '签名时间戳',
  signType: '签名类型',
  sign: '签名串',
  scriptUrl: 'SDK 脚本地址',
  jqueryUrl: 'jQuery 脚本地址',
  email: '合成邮箱（观看身份）',
  username: '观看昵称',
  method_id: '微吼回调 method_id',
  webinar_id: '微吼活动号',
  sender_id: '发送方/观众 id',
  signature: '回调签名',
  context: '回调上下文',
  updated: '成功更新条数',
  skipped: '跳过条数',
  missing: '未找到条数',
  contentCount: '内容数（本轮恒为 0）',
  entitledUserCount: '权益用户数（本轮恒为 0）',
  totalUserCount: '累计用户数（本轮恒为 0）',
  salesCount: '总销量（本轮恒为 0）',
  coursewareMaterialIds: '课件素材 UUID 有序数组',
  materialIds: '课件素材 UUID 列表',
  materialId: '录播视频素材 UUID（素材中心 type=video）',
  recordMaterialId: '本场绑定的录播素材 UUID',
  recordId: '微吼回放/点播资源 id（create-demand 的 record_id）',
  downloadUrl: '回放打包下载地址（微吼 download_url；status=1 时可用，约 7 天有效）',
  progress: '打包进度 0～100',
  ready: '就绪状态：1 就绪 / 0 转码中 / 2 失败',
  isTiming: '录播是否定时直播（对应微吼 is_timing）',
  data: '业务数据；失败时可能省略',
  provider: '直播服务商',
  viewerStyle: '观看端样式',
  shelfStatus: '上架状态',
  riskStatus: '风控审核状态',
  sellType: '售卖方式',
  accessValidityType: '访问有效期类型',
  playbackValidityType: '回放有效期类型',
  aiAuditStatus: 'AI 审核状态',
  riskAuditStatus: '风控合规审核状态',
  role: '开播角色',
  operatorName: '操作人姓名',
  passcode: '9 位数字开播口令',
  viewerId: '观众 id（viewer 表主键）',
  entitlementStatus: '权益状态：0 无 / 1 有',
  inviterDisplayName: '邀请人展示名',
  inviterKind: '邀请人类型：employee 员工 / audience 观众',
  inviteTime: '邀请时间',
  tagIds: '标签 id 列表',
  tagLabels: '标签展示名列表',
  totalWatchDurationSec: '累计观看时长（秒）',
  liveWatchDurationSec: '直播观看时长（秒）',
  replayWatchDurationSec: '回放观看时长（秒）',
  commentCount: '评论条数',
  dealAmount: '成交金额（元）',
  created: '是否新建',
  importId: '导入批次 id',
  failed: '失败条数',
  errors: '失败明细',
  row: 'Excel 行号',
  phone: '手机号',
  importTime: '导入时间',
  importerName: '导入操作人',
  successCount: '成功条数',
  failCount: '失败条数',
  commentTime: '评论时间',
  couponId: '订单中心券池主键 id',
  couponNo: '券编号，如 CPN2401',
  couponName: '券名称',
  couponAmount: '金额字符串，如 500.00；前端展示时加人民币符号与两位小数',
  couponStartDate: '券有效期起',
  couponEndDate: '券有效期止',
  allSku: 'true 表示全部商品可用；省略或 false 则按 skuNames 展示',
  senderName: '发送人昵称快照',
  employeeId: '员工数字 id（rs_mainkey_id）',
  deleted: '是否已删除',
  phoneMasked: '脱敏手机号',
  banComment: '禁言开关',
  banPurchase: '禁止购买开关',
  banAccess: '禁止访问开关',
  lastVisitTime: '最近访问时间',
  consumeCount: '消费次数',
  consumeAmount: '消费金额（元）',
  totalLearnDurationSec: '累计学习时长（秒）',
  todayLearnDurationSec: '今日学习时长（秒）',
  completedCourseCount: '已完成课程数',
  totalLearnDays: '累计学习天数',
  viewedCourseCount: '已观看课程数',
  tagCount: '标签数量',
  definitionSummary: '标签规则摘要',
  userCount: '覆盖用户数',
  groupName: '标签分组名',
  hasPhone: '是否已绑定手机号',
  avatarObjectKey: '头像对象存储 key',
  agreed: '是否同意用户协议',
  loginCode: 'wx.login 临时 code',
  phoneCode: 'getPhoneNumber 返回 code',
  location: '上传后访问 URL',
  expiredTime: 'STS 过期时间',
  cdnUrl: 'CDN 根地址',
  contactPhone: '联系电话',
  images: '截图 objectKey 列表',
  category: '反馈分类 code',
  categoryLabel: '反馈分类展示名',
  imageCount: '截图数量',
  scene: '场景：live / replay',
  tosUrl: '本站 TOS/CDN 播放地址',
  socketId: '回放心跳 socket id',
  lastReportTime: '上次上报时间',
  roleName: '评论角色名',
  msgId: '云消息 id（如微吼 msg_id；聊天通常不传；提问 create 后可补；删除可用历史行）。勿传提问 data.id',
  questionId: '云提问 id（如微吼 data.id；提问上报必传；聊天不传）',
  commentId: '评论 id',
  courseId: '课程 UUID（D74）',
  courseName: '课程名称',
  contentCount: '内容数',
  entitlementState: '权益计算态',
  entitlementStateLabel: '权益状态展示文案',
  acquireWay: '权益获取方式',
  acquireWayLabel: '权益获取方式展示文案',
  firstAcquireTime: '权益首次获取时间',
  expireDisplay: '到期展示文案',
  chapterCompletionRate: '小节完成率',
  accessValidityFixedTime: '固定到期时刻，yyyy-MM-dd HH:mm:ss',
  accessPermission: '课件访问权限',
}

const FIELD_DESC_BY_PATH = {
  'IdData.id': '本站业务主键（活动/素材等多为 UUID）',
  'MaterialGroup.id': '素材分组 UUID',
  'MaterialItem.id': '素材 UUID',
  'ArticleDetail.id': '文章素材 UUID',
  'ActivitySummary.id': '本站活动 UUID',
  'ActivityStats.id': '本站活动 UUID',
  'OnlineTrend.id': '本站活动 UUID',
  'PresenceTrend.id': '本站活动 UUID',
  'SalesSettingsData.id': '本站活动 UUID',
  'ShelfData.id': '本站活动 UUID',
  'AuditSubmitData.id': '本站活动 UUID',
  'AuditRecord.id': '审核记录 UUID',
  'ActivityRecordItem.id': '回放 record_id（create-demand 用）',
  'ActivityRecordDownloadData.recordId': '微吼回放 id（同 GET .../records 的 list[].id）',
  'ActivityRecordDownloadData.packStatus':
    '打包状态：0 未开始 / 1 已完成 / 2 进行中 / 3 失败',
  'ActivityRecordDownloadData.progress': '打包进度 0～100',
  'ActivityRecordDownloadData.downloadUrl':
    '下载地址；status=1 时可用（微吼侧约 7 天有效）',
  'LiveReportResult.id': '报备单 UUID',
  'LiveReportDetail.id': '报备单 UUID',
  'LiveReportAuditItem.id': '审核记录 UUID',
  'CourseSummary.id': '课程 UUID',
  'CourseSummary.listedTime':
    '上架时间，yyyy-MM-dd HH:mm:ss；创建/保存勾选上架或列表手动上架时写入；下架/驳回一般不清；无则不返回',
  'CourseMaterial.id': '素材 UUID',
  'OutsideCourseItem.courseId': '课程 UUID（D74）',
  'OutsideCourseItem.courseName': '课程名称',
  'OutsideCourseItem.contentCount': '内容数',
  'OutsideCourseItem.departmentName': '课程部门（归属人部门）',
  'WatchActivitySummary.id': '本站活动 UUID',
  'WatchEmbedData.id': '本站活动 UUID',
  'WatchSummaryData.id': '本站活动 UUID',
  'WatchSummaryData.provider': '服务商（页壳可忽略；init 仍以 sdk-sign 的 provider 为准）',
  'WatchSummaryData.title': '本库活动标题',
  'WatchSummaryData.state': '本库活动状态（D106：vhall 可能刚被 webinar/info 对账写回）',
  'WatchSummaryData.type': '本库活动类型',
  'WatchSummaryData.startTime': '计划/实际开始，yyyy-MM-dd HH:mm:ss；本库',
  'WatchSummaryData.endTime': '结束时间；占位 0000-00-00… 时不输出',
  'WatchSummaryData.playbackEnabled': '本站回放开关；false 时结束页不展示「查看回放」',
  'WatchSummaryData.playbackValidityType': 'forever 永久有效 / days 直播结束后 N 天有效；缺省按 forever',
  'WatchSummaryData.playbackValidityDays': 'type=days 时的天数；永久有效时可省略',
  'WatchSummaryData.playbackExpired':
    '是否已超过有效期；起算同 replay/start：liveActualEndTime → 计划 endTime；无法起算则 false',
  'WatchSummaryData.playbackSpeedAllow': '是否允许倍速（原生 video.playbackRate）；缺省 true',
  'WatchSummaryData.playbackFastForwardAllow': '是否允许快进/拖进度；缺省 true',
  'WatchSdkData.id': '本站活动 UUID（与 Query id 相同）',
  'WatchSdkData.provider': '服务商；前端 createWatchAdapter(provider)',
  'WatchSdkData.viewerId': '观众数字 id（与 sdk.email 一致）',
  'WatchSdkData.nickname': '与 sdk.username 同值（身份回显）',
  'WatchSdkData.sdk': '该服务商签名与 init 鉴权参数（不含脚本 CDN，D93）',
  'WatchAccessData.accessState':
    'ok 已放行 / need_password 已登录但缺观看密码 / need_login 需登录（含免费场） / forbidden 无观看权限 / expired 访问已过期',
  'WatchAccessData.accessExpired': '访问是否已过期（days 起算：仅 liveActualEndTime）',
  'WatchAccessData.sellType':
    'free 免费 / paid 付费（存量） / encrypted 加密 / specified 指定学员',
  'WatchAccessData.accessValidityType': 'long 长期有效 / days 直播实际结束后 N 天有效',
  'WatchAccessVerifyPasswordData.accessToken': '本场访问 JWT（请求头 X-Activity-Access）',
  'WatchAccessVerifyPasswordData.expiresIn': 'accessToken 有效秒数',
  'WatchChatHistoryItem.senderId': '发送方 id',
  'WatchChatHistoryItem.senderName': '发送方昵称',
  'WatchChatHistoryItem.msg': '正文',
  'WatchChatHistoryItem.msgId': '消息 id；可空',
  'WatchChatHistoryItem.avatar': '头像；可空',
  'WatchChatHistoryItem.timestamp': '毫秒时间戳',
  'WatchChatHistoryItem.roleName': '1 主持 / 2 观众 / 3 助理 / 4 嘉宾；可空',
  'WatchChatHistoryData.total': '微吼总数；失败为 0',
  'WatchChatHistoryData.page': '回显请求页码',
  'WatchChatHistoryData.pageSize': '回显每页条数（固定 50）',
  'WatchChatHistoryData.list': '旧→新；仅文本类消息',
  'WatchQuestionHistoryItem.questionId':
    '云提问 id（room_comment.question_id）；前端作 ChatMessage.msgId 对齐键；可空',
  'WatchQuestionHistoryItem.senderId': '发言观众 id；补建行可空',
  'WatchQuestionHistoryItem.senderName': '昵称快照；库空时服务端可兜底「观众」',
  'WatchQuestionHistoryItem.msg': '提问正文',
  'WatchQuestionHistoryItem.msgId': '云消息 id（room_comment.msg_id）；可空',
  'WatchQuestionHistoryItem.roleName': '1 主持 / 2 观众 / 3 助理 / 4 嘉宾；可空',
  'WatchQuestionHistoryItem.timestamp': 'comment_time 毫秒（上海时区）',
  'WatchQuestionHistoryItem.answerContent': '主持人/嘉宾回复；无可空',
  'WatchQuestionHistoryItem.answerNickname': '回复人昵称；可空',
  'WatchQuestionHistoryItem.answerRoleName': '回复人角色数字；可空',
  'WatchQuestionHistoryData.total': '本场未软删提问行数（截断前）',
  'WatchQuestionHistoryData.list': '旧→新；最多 500 条',
  'ConsoleQuestionHistoryData.total': '本场未软删提问行数（截断前）',
  'ConsoleQuestionHistoryData.list': '旧→新；最多 500 条；形状同 WatchQuestionHistoryItem',
  'CoursewareItem.id': '素材 UUID',
  'AttendeeItem.online': '是否在线',
  'PresenceTrendPoint.online': '该桶在线人数',
  'ActivityDetail.price': '售价（元）；非付费可不返回',
  'ActivityDetail.linePrice': '划线价（元）；无则可不返回',
  'AuditSubmitRequest.result': 'passed 已通过 / rejected 已驳回',
  'LiveReportAuditRequest.result': 'passed 已通过 / rejected 已驳回',
  'LiveReportAuditItem.action':
    'submit 提交 / resubmit 重新提交 / audit 审核 / append 追加计划时间',
  'LiveReportAuditItem.result': 'passed 已通过 / rejected 已驳回。非审核动作可不返回',
  'MaterialItem.status': '素材状态，如 available',
  'VhallPresenceCallback.type': 'Join 进房 / Leave 离房',
  'LoginQueryData.user': '扫码成功后的员工信息',
  'LiveReportDetail.host': '报备主播',
  // 观众标签分组为 BIGINT，勿用全局「分组 UUID」文案
  'ViewerTagListItem.groupId': '标签分组 id（viewer_tag_group.id）',
  'ViewerTagCreateRequest.groupId': '标签分组 id（viewer_tag_group.id）；省略则归入默认分组',
  'ViewerTagUpdateRequest.groupId': '标签分组 id（viewer_tag_group.id）',
  'ViewerTagGroupBrief.id': '标签分组主键',
  'ViewerTagGroupPageItem.id': '标签分组主键',
  'ViewerTagListItem.id': '标签主键',
}

/** 按字段名补落库类型 / 长度（D61）；已有 maxLength 的保留。 */
const FIELD_DB_BY_NAME = {
  id: { db: 'VARCHAR(32)', maxLength: 32, pattern: "^[0-9a-f]{32}$" }, // D74 business default
  title: { db: 'VARCHAR(64)', maxLength: 64 },
  name: { db: 'VARCHAR(256)', maxLength: 256 },
  author: { db: 'VARCHAR(16)', maxLength: 16 },
  introduction: { db: 'VARCHAR(256)', maxLength: 256 },
  content: { db: 'VARCHAR(500)', maxLength: 500 },
  detailHtml: { db: 'TEXT', maxLength: 100000 },
  reason: { db: 'VARCHAR(500)', maxLength: 500 },
  remark: { db: 'VARCHAR(256)', maxLength: 256 },
  nickname: { db: 'VARCHAR(64)', maxLength: 64 },
  userName: { db: 'VARCHAR(50)', maxLength: 50 },
  userAccount: { db: 'VARCHAR(64)', maxLength: 64 },
  departmentName: { db: 'VARCHAR(256)', maxLength: 256 },
  roleNames: { db: 'VARCHAR(256)', maxLength: 256 },
  operatorName: { db: 'VARCHAR(50)', maxLength: 50 },
  submitterName: { db: 'VARCHAR(50)', maxLength: 50 },
  uploaderName: { db: 'VARCHAR(50)', maxLength: 50 },
  productName: { db: 'VARCHAR(128)', maxLength: 128 },
  validityLabel: { db: 'VARCHAR(64)', maxLength: 64 },
  password: { db: 'VARCHAR(64)', maxLength: 64 },
  url: { db: 'VARCHAR(2048)', maxLength: 2048 },
  coverUrl: { db: 'VARCHAR(2048)', maxLength: 2048 },
  thumbUrl: { db: 'VARCHAR(2048)', maxLength: 2048 },
  avatar: { db: 'VARCHAR(512)', maxLength: 512 },
  avatarUrl: { db: 'VARCHAR(512)', maxLength: 512 },
  objectKey: { db: 'VARCHAR(1024)', maxLength: 1024 },
  coverObjectKey: { db: 'VARCHAR(1024)', maxLength: 1024 },
  mimeType: { db: 'VARCHAR(128)', maxLength: 128 },
  coverMimeType: { db: 'VARCHAR(128)', maxLength: 128 },
  meta: { db: 'VARCHAR(128)', maxLength: 128 },
  groupId: { db: 'VARCHAR(32)', maxLength: 32, pattern: "^[0-9a-f]{32}$" },
  reportId: { db: 'VARCHAR(32)', maxLength: 32, pattern: "^[0-9a-f]{32}$" },
  coverMaterialId: { db: 'VARCHAR(32)', maxLength: 32, pattern: "^[0-9a-f]{32}$" },
  materialId: { db: 'VARCHAR(32)', maxLength: 32, pattern: "^[0-9a-f]{32}$" },
  recordMaterialId: { db: 'VARCHAR(32)', maxLength: 32, pattern: "^[0-9a-f]{32}$" },
  recordId: { db: '微吼 integer（本站不落库，透传 create-demand）' },
  ready: { db: 'INT' },
  captchaId: { db: 'VARCHAR(36)', maxLength: 36, format: 'uuid' },
  providerRoomId: { db: 'VARCHAR(64)', maxLength: 64 },
  hostUrl: { db: 'VARCHAR(1024)', maxLength: 1024 },
  headimgurl: { db: 'VARCHAR(500)', maxLength: 500 },
  embedUrl: { db: 'VARCHAR(1024)', maxLength: 1024 },
  pageUrl: { db: 'VARCHAR(2048)', maxLength: 2048 },
  guestId: { db: 'VARCHAR(64)', maxLength: 64 },
  userId: { db: 'VARCHAR(128)', maxLength: 128 },
  mid: { db: 'VARCHAR(64)', maxLength: 64 },
  email: { db: 'VARCHAR(128)', maxLength: 128 },
  username: { db: 'VARCHAR(50)', maxLength: 50 },
  tag: { db: 'VARCHAR(32)', maxLength: 32 },
  spuId: { db: 'VARCHAR(64)', maxLength: 64 },
  startTime: { db: 'DATETIME(3)' },
  endTime: { db: 'DATETIME(3)' },
  createdTime: { db: 'DATETIME(3)' },
  updatedTime: { db: 'DATETIME(3)' },
  submittedTime: { db: 'DATETIME(3)' },
  uploadedTime: { db: 'DATETIME(3)' },
  listedTime: { db: 'DATETIME(3)' },
  deletedTime: { db: 'DATETIME(3)' },
  enterTime: { db: 'DATETIME(3)' },
  leaveTime: { db: 'DATETIME(3)' },
  expireTime: { db: 'DATETIME(3)' },
  fileSize: { db: 'BIGINT', format: 'int64' },
  coverFileSize: { db: 'BIGINT', format: 'int64' },
  total: { db: 'BIGINT', format: 'int64' },
  page: { db: 'INT' },
  pageSize: { db: 'INT' },
  price: { db: 'DECIMAL(10,2)', type: 'number', multipleOf: 0.01 },
  linePrice: { db: 'DECIMAL(10,2)', type: 'number', multipleOf: 0.01 },
  code: { db: 'INT' },
  msg: { db: 'VARCHAR(256)', maxLength: 256 },
  provider: { db: 'VARCHAR(16)', maxLength: 16 },
  shelfStatus: { db: 'VARCHAR(16)', maxLength: 16 },
  riskStatus: { db: 'VARCHAR(16)', maxLength: 16 },
  sellType: { db: 'VARCHAR(16)', maxLength: 16 },
  accessValidityType: { db: 'VARCHAR(16)', maxLength: 16 },
  playbackValidityType: { db: 'VARCHAR(16)', maxLength: 16 },
  viewerStyle: { db: 'VARCHAR(32)', maxLength: 32 },
  warmupType: { db: 'VARCHAR(16)', maxLength: 16 },
  status: { db: 'VARCHAR(16)', maxLength: 16 },
  action: { db: 'VARCHAR(16)', maxLength: 16 },
  result: { db: 'VARCHAR(16)', maxLength: 16 },
  type: { db: 'INT' },
  state: { db: 'INT' },
  role: { db: 'INT' },
  verify: { db: 'INT' },
  aiAuditStatus: { db: 'INT' },
  riskAuditStatus: { db: 'INT' },
  autoList: { db: 'TINYINT(1)' },
  sellAlone: { db: 'TINYINT(1)' },
  playbackEnabled: { db: 'TINYINT(1)' },
  playbackSpeedAllow: { db: 'TINYINT(1)' },
  playbackFastForwardAllow: { db: 'TINYINT(1)' },
  liveEnabled: { db: 'TINYINT(1)' },
  enabled: { db: 'TINYINT(1)' },
  listed: { db: 'TINYINT(1)' },
  canBroadcast: { db: 'TINYINT(1)' },
  canList: { db: 'TINYINT(1)' },
  canUnlist: { db: 'TINYINT(1)' },
  isDefault: { db: 'TINYINT(1)' },
  ultraLowLatency: { db: 'TINYINT(1)' },
  online: { db: 'TINYINT(1)' },
  playbackValidityDays: { db: 'INT' },
  accessValidityDays: { db: 'INT' },
  accessValidityFixedTime: { db: 'DATETIME(3)' },
  accessPermission: { db: 'INT' },
  chapterCompletionRate: { db: 'DECIMAL(10,2)' },
  contentCount: { db: 'INT' },
  entitledUserCount: { db: 'INT' },
  totalUserCount: { db: 'INT' },
  salesCount: { db: 'INT' },
  boundLiveCount: { db: 'INT' },
  expiresIn: { db: 'INT' },
  refreshExpiresIn: { db: 'INT' },
  count: { db: 'INT' },
  onlineCount: { db: 'INT' },
  pv: { db: 'INT' },
  enter: { db: 'INT' },
  leave: { db: 'INT' },
  bucketSeconds: { db: 'INT' },
  updated: { db: 'INT' },
  skipped: { db: 'INT' },
  missing: { db: 'INT' },
}

/** 路径特例覆盖库类型（schema.字段；嵌套用 members[].nickname 形）。 */
const FIELD_DB_BY_PATH = {
  'MaterialGroup.name': { db: 'VARCHAR(64)', maxLength: 64 },
  'ArticleCreateRequest.title': { db: 'VARCHAR(45)', maxLength: 45 },
  'ArticleCreateRequest.content': { db: 'MEDIUMTEXT', maxLength: 100000 },
  'ArticleDetail.title': { db: 'VARCHAR(45)', maxLength: 45 },
  'ArticleDetail.content': { db: 'MEDIUMTEXT', maxLength: 100000 },
  'ArticleUpdateRequest.title': { db: 'VARCHAR(45)', maxLength: 45 },
  'ArticleUpdateRequest.content': { db: 'MEDIUMTEXT', maxLength: 100000 },
  'CreateCourseRequest.title': { db: 'VARCHAR(45)', maxLength: 45 },
  'CreateCourseRequest.detailHtml': { db: 'MEDIUMTEXT', maxLength: 200000 },
  'CourseSummary.title': { db: 'VARCHAR(45)', maxLength: 45 },
  'CourseDetail.title': { db: 'VARCHAR(45)', maxLength: 45 },
  'CourseDetail.detailHtml': { db: 'MEDIUMTEXT', maxLength: 200000 },
  'OutsideCourseItem.courseName': { db: 'VARCHAR(45)', maxLength: 45 },
  'OutsideCourseItem.contentCount': { db: 'INT' },
  'OutsideCourseItem.departmentName': { db: 'VARCHAR(255)' },
  'LiveReportSaveRequest.content': { db: 'VARCHAR(500)', maxLength: 500 },
  'LiveReportUpdateRequest.content': { db: 'VARCHAR(500)', maxLength: 500 },
  'LiveReportDetail.content': { db: 'VARCHAR(500)', maxLength: 500 },
  'MaterialRegisterRequest.items[].name': { db: 'VARCHAR(256)', maxLength: 256 },
  'CoursewareItem.name': { db: 'VARCHAR(256)', maxLength: 256 },
  'CourseMaterial.name': { db: 'VARCHAR(256)', maxLength: 256 },
  'AddActivityMembersRequest.members[].nickname': {
    db: 'VARCHAR(64)',
    maxLength: 64,
  },
  'AddActivityMembersRequest.members[].avatarUrl': {
    db: 'VARCHAR(512)',
    maxLength: 512,
  },
  'AddActivityMembersRequest.members[].tag': {
    db: 'VARCHAR(32)',
    maxLength: 32,
  },
  'UpdateActivityMemberRequest.nickname': {
    db: 'VARCHAR(64)',
    maxLength: 64,
  },
  'UpdateActivityMemberRequest.avatarUrl': {
    db: 'VARCHAR(512)',
    maxLength: 512,
  },
  'UpdateActivityMemberRequest.tag': {
    db: 'VARCHAR(32)',
    maxLength: 32,
  },
  'ActivityMemberItem.nickname': { db: 'VARCHAR(64)', maxLength: 64 },
  'ActivityMemberItem.avatarUrl': { db: 'VARCHAR(512)', maxLength: 512 },
  'ActivityMemberItem.tag': { db: 'VARCHAR(32)', maxLength: 32 },
  'ActivityRecordItem.id': { db: '微吼 integer（不落本站库）' },
  'ActivityRecordDownloadData.recordId': { db: '微吼 integer（不落本站库）' },
  'ActivityRecordDownloadData.downloadUrl': { db: '微吼透传（不落本站库）' },
  'ActivityRecordDownloadData.status': { db: '微吼透传（不落本站库）' },
  'ActivityRecordDownloadData.progress': { db: '微吼透传（不落本站库）' },
  'AttendeeItem.online': { db: 'TINYINT(1)' },
  'PresenceTrendPoint.online': { db: 'INT' },
  'AuditSubmitRequest.reason': { db: 'VARCHAR(200)', maxLength: 200 },
  'LiveReportAuditRequest.reason': { db: 'VARCHAR(500)', maxLength: 500 },
  // 观众标签：库列宽于 API 校验长度；id/groupId 为 BIGINT 自增（勿套业务 UUID）
  'ViewerTagGroupBrief.id': { db: 'BIGINT', format: 'int64' },
  'ViewerTagGroupBrief.name': { db: 'VARCHAR(32)', maxLength: 10 },
  'ViewerTagGroupPageItem.id': { db: 'BIGINT', format: 'int64' },
  'ViewerTagGroupPageItem.name': { db: 'VARCHAR(32)', maxLength: 10 },
  'ViewerTagGroupCreateRequest.name': { db: 'VARCHAR(32)', maxLength: 10 },
  'ViewerTagGroupUpdateRequest.name': { db: 'VARCHAR(32)', maxLength: 10 },
  'ViewerTagListItem.id': { db: 'BIGINT', format: 'int64' },
  'ViewerTagListItem.name': { db: 'VARCHAR(20)', maxLength: 20 },
  'ViewerTagListItem.groupId': { db: 'BIGINT', format: 'int64' },
  'ViewerTagListItem.definitionSummary': { db: 'VARCHAR(512)', maxLength: 512 },
  'ViewerTagCreateRequest.name': { db: 'VARCHAR(20)', maxLength: 20 },
  'ViewerTagCreateRequest.groupId': { db: 'BIGINT', format: 'int64' },
  'ViewerTagUpdateRequest.name': { db: 'VARCHAR(20)', maxLength: 20 },
  'ViewerTagUpdateRequest.groupId': { db: 'BIGINT', format: 'int64' },
  // 组织部门编码来自总部表 bmbm（字符串）；勿套全局 code→INT（API 业务码）
  'OrgDeptNode.code': { db: 'VARCHAR(64)', maxLength: 64 },
}

const DB_ANNOTATION_SKIP = new Set([
  'data',
  'list',
  'items',
  'children',
  'owners',
  'products',
  'hosts',
  'coursewares',
  'materials',
  'attachments',
  'planTimes',
  'members',
  'menus',
  'menuCodes',
  'points',
  'user',
  'host',
  'sdk',
])

function fillChineseDescriptions(schemas) {
  const missing = []

  function fillNode(schemaName, node, prefix) {
    if (!node || typeof node !== 'object' || Array.isArray(node)) return
    if (node.properties) {
      for (const [key, prop] of Object.entries(node.properties)) {
        if (!prop || typeof prop !== 'object') continue
        const path = prefix ? `${prefix}.${key}` : key
        const full = `${schemaName}.${path}`
        if (!prop.description) {
          const refName = prop.$ref ? String(prop.$ref).split('/').pop() : ''
          const refSchema = refName ? schemas[refName] : null
          // 枚举 $ref 优先用组件上的「取值 中文」；字段名默认文案（类型/状态）不列取值，Apifox 会只显示 1/2/3/4
          const d =
            FIELD_DESC_BY_PATH[full] ||
            (refSchema && refSchema.description) ||
            FIELD_DESC_BY_NAME[key]
          if (d) {
            prop.description = d
          } else {
            missing.push(full)
          }
        }
        fillNode(schemaName, prop, path)
        if (prop.items) fillNode(schemaName, prop.items, `${path}[]`)
      }
    }
    if (Array.isArray(node.allOf)) {
      for (const part of node.allOf) fillNode(schemaName, part, prefix)
    }
  }

  for (const [name, schema] of Object.entries(schemas)) {
    fillNode(name, schema, '')
  }

  if (missing.length) {
    const sample = missing.slice(0, 40).join('\n  ')
    throw new Error(
      `OpenAPI D58：仍有 ${missing.length} 个字段缺中文 description，请补 FIELD_DESC_BY_NAME / FIELD_DESC_BY_PATH：\n  ${sample}`,
    )
  }
}

function dbFromMaxLength(max) {
  // MySQL：TEXT≤65535，更大用 MEDIUMTEXT（与 Flyway 文章/课程正文一致）
  if (max > 65535) return 'MEDIUMTEXT'
  if (max > 16383) return 'TEXT'
  return `VARCHAR(${max})`
}

/** 字段名默认库类型是否与 OpenAPI type/format 冲突（如 int64 的 id 不要套 UUID VARCHAR(32)）。 */
function hintCompatibleWithProp(hint, prop) {
  if (!hint || !hint.db || !prop) return true
  const db = String(hint.db)
  // 透传 / 不落库 文案不做类型冲突判断
  if (/透传|不落|微吼/.test(db)) return true
  const stringDb = /^(VARCHAR|CHAR|TEXT|MEDIUMTEXT|LONGTEXT)\b/i.test(db)
  const intDb = /^(BIGINT|INT|TINYINT|SMALLINT|MEDIUMINT)\b/i.test(db)
  const decimalDb = /^(DECIMAL|NUMERIC|DOUBLE|FLOAT)\b/i.test(db)
  const propInt =
    prop.type === 'integer' ||
    prop.format === 'int64' ||
    prop.format === 'int32'
  if (propInt && (stringDb || decimalDb)) return false
  if (prop.type === 'boolean' && (stringDb || decimalDb || (/^(BIGINT|INT|SMALLINT|MEDIUMINT)\b/i.test(db) && !/^TINYINT\b/i.test(db)))) {
    return false
  }
  if (prop.type === 'number' && (stringDb || intDb)) return false
  if (prop.type === 'string' && (intDb || decimalDb)) return false
  return true
}

/**
 * @param fromPath 路径特例（可表达「API maxLength≠库列长度」，如昵称校验 10 / 库 VARCHAR(64)）
 */
function inferDbLabel(prop, key, hint, fromPath) {
  if (!prop || prop.$ref) return null
  // 路径特例：库类型原文始终听 hint（校验长度可严于库列）
  if (fromPath && hint && hint.db) return hint.db
  // 按字段名提示：与 OpenAPI 类型冲突时丢弃（避免 id/groupId/status/code/type 等同名异型）
  // 与已有 maxLength 冲突时改按 maxLength 推断（避免 content 默认 500 盖住文章 100000）
  if (hint && hint.db && hintCompatibleWithProp(hint, prop)) {
    const maxConflict =
      typeof prop.maxLength === 'number' &&
      typeof hint.maxLength === 'number' &&
      prop.maxLength !== hint.maxLength
    if (!maxConflict) return hint.db
  }
  if (prop.pattern === "^[0-9a-f]{32}$") return 'VARCHAR(32)'
  if (prop.format === 'uuid') return 'VARCHAR(36)'
  if (prop.format === 'int64') return 'BIGINT'
  if (prop.type === 'boolean') return 'TINYINT(1)'
  if (prop.type === 'number') {
    return 'DECIMAL(10,2)'
  }
  if (prop.type === 'integer') {
    return 'INT'
  }
  if (prop.type === 'string') {
    if (
      /Time$/.test(key) ||
      key === 'time' ||
      key === 'expireTime'
    ) {
      return 'DATETIME(3)'
    }
    if (typeof prop.maxLength === 'number') {
      return dbFromMaxLength(prop.maxLength)
    }
  }
  if (hint && hint.db && hintCompatibleWithProp(hint, prop)) return hint.db
  return null
}

/**
 * D61：补缺的长度/精度约束，并在 description 末尾附「库：…」（已有则跳过）。
 */
function annotateDbConstraints(schemas) {
  function walk(schemaName, node, prefix) {
    if (!node || typeof node !== 'object' || Array.isArray(node)) return
    if (node.properties) {
      for (const [key, prop] of Object.entries(node.properties)) {
        if (!prop || typeof prop !== 'object') continue
        const path = prefix ? `${prefix}.${key}` : key
        const full = `${schemaName}.${path}`
        const fromPath = Object.prototype.hasOwnProperty.call(
          FIELD_DB_BY_PATH,
          full,
        )
        const hint = fromPath
          ? FIELD_DB_BY_PATH[full]
          : FIELD_DB_BY_NAME[key]

        // 字段名默认与 OpenAPI 类型冲突时不套用（如 int64 id 不套 UUID maxLength/pattern）
        if (
          !prop.$ref &&
          hint &&
          (fromPath || hintCompatibleWithProp(hint, prop))
        ) {
          if (hint.type && !prop.type) prop.type = hint.type
          if (hint.format && !prop.format) prop.format = hint.format
          // 仅补缺 maxLength；已有业务校验长度（可严于库列）不覆盖
          if (
            typeof hint.maxLength === 'number' &&
            prop.maxLength === undefined &&
            (prop.type === 'string' || !prop.type)
          ) {
            if (!prop.type) prop.type = 'string'
            prop.maxLength = hint.maxLength
          }
          if (
            typeof hint.multipleOf === 'number' &&
            prop.multipleOf === undefined
          ) {
            prop.multipleOf = hint.multipleOf
          }
        }

        if (
          !prop.$ref &&
          !DB_ANNOTATION_SKIP.has(key) &&
          prop.type !== 'object' &&
          prop.type !== 'array'
        ) {
          const db = inferDbLabel(prop, key, hint, fromPath)
          if (db && prop.description && !/库[：:]/.test(prop.description)) {
            prop.description = `${prop.description}；库：${db}`
          }
        }

        walk(schemaName, prop, path)
        if (prop.items) walk(schemaName, prop.items, `${path}[]`)
      }
    }
    if (Array.isArray(node.allOf)) {
      for (const part of node.allOf) walk(schemaName, part, prefix)
    }
  }

  for (const [name, schema] of Object.entries(schemas)) {
    // 顶层枚举 / 标量 schema（如 EmployeeId、ShelfStatus）
    if (
      schema &&
      typeof schema === 'object' &&
      !schema.properties &&
      schema.description &&
      !/库[：:]/.test(schema.description)
    ) {
      let db = null
      if (schema.format === 'int64') db = 'BIGINT'
      else if (schema.type === 'integer') db = 'INT'
      else if (schema.type === 'string' && schema.enum) {
        const maxLen = Math.max(
          ...schema.enum.map((v) => String(v).length),
          1,
        )
        const padded = Math.min(Math.max(maxLen, 16), 64)
        db = `VARCHAR(${padded})`
      } else if (schema.type === 'string' && schema.format === 'uuid') {
        db = 'VARCHAR(36)'
      }
      if (db) schema.description = `${schema.description}；库：${db}`
    }
    walk(name, schema, '')
  }
}

function finalizeSchemas(schemas) {
  fillChineseDescriptions(schemas)
  annotateDbConstraints(schemas)
}

/**
 * 从 description 的「取值 中文 / 取值 中文」解析每个枚举项。
 * 会去掉「；库：…」、句号后的补充说明，以及括号里带逗号的长注释。
 * 解析不全返回 null。
 */
function parseEnumLabels(values, description) {
  const text = String(description || '')
    .replace(/；库：[\s\S]*$/, '')
    .replace(/。[\s\S]*$/, '')
    .replace(/（[^）]*[，,、/／；;][^）]*）/g, '')
    .replace(/\([^)]*[，,、/／；;][^)]*\)/g, '')
    .trim()
  if (!text) return null
  const parts = text.split(
    /\s*[/／]\s*|\s*[，,]\s*(?=\d+\s|\d+[\u4e00-\u9fff]|[A-Za-z][A-Za-z0-9_]*\s)/,
  )
  const map = new Map()
  for (const raw of parts) {
    const part = raw.trim()
    if (!part) continue
    const matched = part.match(
      /^(?:[\u4e00-\u9fff]{1,8}[：:])?\s*(\d+|[A-Za-z][A-Za-z0-9_]*)\s*(.+)$/,
    )
    if (!matched) continue
    const label = matched[2].trim()
    if (!label) continue
    map.set(matched[1], label)
  }
  const items = []
  for (const value of values) {
    const label = map.get(String(value))
    if (!label) return null
    items.push({ value, name: label, description: label })
  }
  return items
}

/**
 * Apifox 枚举「说明」列只认 x-apifox-enum，不拆 description。
 * 每个带 enum 的 schema（含 Query 内联）都写上；再抄到 $ref 字段上，避免引用处被收成「类型/状态」。
 */
function attachApifoxEnums(doc) {
  const failures = []
  const seen = new Set()
  let count = 0

  function visit(node, path) {
    if (!node || typeof node !== 'object') return
    if (seen.has(node)) return
    seen.add(node)
    if (Array.isArray(node.enum) && node.enum.length) {
      const items = parseEnumLabels(node.enum, node.description)
      if (!items) {
        failures.push(
          `${path} enum=${JSON.stringify(node.enum)} desc=${JSON.stringify(node.description || '')}`,
        )
      } else {
        node['x-apifox-enum'] = items
        count += 1
      }
    }
    if (Array.isArray(node)) {
      node.forEach((item, i) => visit(item, `${path}[${i}]`))
      return
    }
    for (const [key, value] of Object.entries(node)) {
      if (key === 'x-apifox-enum') continue
      visit(value, path ? `${path}.${key}` : key)
    }
  }

  visit(doc, 'doc')

  const schemas = doc.components && doc.components.schemas
  if (schemas) {
    const seenRef = new Set()
    function copyRef(node) {
      if (!node || typeof node !== 'object' || seenRef.has(node)) return
      seenRef.add(node)
      if (node.$ref && !node['x-apifox-enum']) {
        const name = String(node.$ref).split('/').pop()
        const target = schemas[name]
        if (target && target['x-apifox-enum']) {
          node['x-apifox-enum'] = target['x-apifox-enum']
        }
      }
      if (Array.isArray(node)) {
        node.forEach(copyRef)
        return
      }
      for (const value of Object.values(node)) copyRef(value)
    }
    copyRef(doc)
  }

  if (failures.length) {
    throw new Error(
      `OpenAPI 枚举缺少可解析的中文释义，无法生成 x-apifox-enum：\n  ${failures.join('\n  ')}`,
    )
  }
  console.log(`x-apifox-enum: ${count}`)
}

function buildAdmin() {
  const schemas = {
    BusinessUuid: { ...BUSINESS_UUID },
    ApiResponseBase: {
      type: 'object',
      required: ['code', 'msg'],
      properties: {
        code: {
          type: 'integer',
          description: '0 成功；400/401/403/500/501/502…',
          example: 0,
        },
        msg: { type: 'string', example: 'ok' },
      },
    },
    Provider: {
      type: 'string',
      enum: ['vhall', 'aliyun', 'tencent'],
      description: 'vhall 微吼 / aliyun 阿里云 / tencent 腾讯云。创建后不可改',
    },
    ActivityType: {
      type: 'integer',
      enum: [1, 2, 3, 4],
      description: '1 音频 / 2 视频 / 3 互动 / 4 录播。创建后不可改',
    },
    ActivityState: {
      type: 'integer',
      enum: [0, 1, 2, 3, 4, 5],
      description: '0 全部(列表筛) / 1 直播 / 2 预告 / 3 结束 / 4 点播 / 5 回放',
    },
    ViewerStyle: {
      type: 'string',
      enum: ['traditional', 'immersive'],
      description: 'traditional 传统直播间 / immersive 沉浸直播间',
    },
    WarmupType: {
      type: 'string',
      enum: ['image', 'video'],
      description: 'image 暖场图 / video 暖场视频',
    },
    ShelfStatus: {
      type: 'string',
      enum: ['pending', 'on', 'off', 'banned'],
      description: 'pending 待上架 / on 已上架 / off 已下架 / banned 强制封禁',
    },
    RiskStatus: {
      type: 'string',
      enum: ['pending', 'passed', 'rejected'],
      description: 'pending 待审核 / passed 已通过 / rejected 已驳回',
    },
    SellType: {
      type: 'string',
      enum: ['free', 'paid', 'encrypted', 'specified'],
      description:
        'free 免费 / paid 付费（已下线：列表/详情可读存量；PUT …/sales 不接受，传入返回「sellType 无效」）/ encrypted 加密 / specified 指定学员',
    },
    AccessValidityType: {
      type: 'string',
      enum: ['long', 'days', 'fixed'],
      description:
        'long 长期有效 / days N 天有效（直播：实际结束后起算；课程：按学员 created_time）/ fixed 固定时刻到期（仅课程）',
    },
    CourseSellType: {
      type: 'string',
      enum: ['free', 'encrypted', 'specified'],
      description:
        'free 免费 / encrypted 加密 / specified 指定学员。课程无付费售卖：PUT …/sales 不接受 paid（传 paid → sellType 无效）；列表/详情也不会写入 paid',
    },
    CourseEntitlementState: {
      type: 'string',
      enum: ['valid', 'expired', 'invalid'],
      description: 'valid 有效 / expired 已到期 / invalid 已失效',
    },
    MaterialAccessPermission: {
      type: 'integer',
      enum: [1, 2, 3],
      description: '1 预览及下载 / 2 仅预览 / 3 仅下载；库：INT',
    },
    PlaybackValidityType: {
      type: 'string',
      enum: ['forever', 'days'],
      description: 'forever 永久有效 / days 直播结束后 N 天有效',
    },
    MaterialType: {
      type: 'string',
      enum: ['image', 'video', 'document', 'article'],
      description: 'image 图片 / video 视频 / document 文档 / article 文章',
    },
    MaterialFileType: {
      type: 'string',
      enum: ['image', 'video', 'document'],
      description:
        'image 图片 / video 视频 / document 文档。批量登记仅接受文件类型，不含 article',
    },
    AuditWriteStatus: {
      type: 'integer',
      enum: [1, 2],
      description: '1 已通过 / 2 已驳回。不接受 0',
    },
    MaterialAuditStatus: {
      type: 'integer',
      enum: [0, 1, 2],
      description: '0 待审核 / 1 已通过 / 2 已驳回',
    },
    ActivityMemberRole: {
      type: 'integer',
      enum: [1, 2, 3],
      description: '1 主播 / 2 嘉宾 / 3 助理（D66，与微吼 get-role-url type 一致；JSON 只传数字）',
    },
    // 员工 / 部门 / 角色等 BIGINT 主键（D56：JSON number，禁止十进制 string）
    EmployeeId: {
      type: 'integer',
      format: 'int64',
      description: '员工 rs_mainkey_id（JSON 数字，D56）',
      example: 10001,
    },
    IdData: {
      type: 'object',
      required: ['id'],
      properties: { id: { $ref: '#/components/schemas/BusinessUuid' } },
    },
    /** 自增数字主键回包（标签/标签分组等 BIGINT；勿与业务 UUID 的 IdData 混用） */
    IdDataInt: {
      type: 'object',
      required: ['id'],
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: '自增主键 id（BIGINT），如标签或标签分组',
        },
      },
    },
    EmployeeBrief: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/EmployeeId' },
        userName: { type: 'string', description: '员工姓名' },
        avatar: { type: 'string', description: '头像 URL；没有则不返回' },
        userAccount: { type: 'string', description: '登录账号；候选接口可不返回' },
        departmentName: {
          type: 'string',
          description: '员工候选：qw_bmxx，多部门用顿号连接；没有则不返回',
        },
      },
    },
    ProductRef: {
      type: 'object',
      required: ['spuId', 'productName'],
      properties: {
        spuId: {
          type: 'string',
          description:
            '订单中心商品 id；可能超过 JS 安全整数，故用十进制字符串（D56 例外）',
        },
        productName: { type: 'string', minLength: 1, maxLength: 128 },
      },
    },
    CoursewareItem: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        name: { type: 'string' },
        type: { $ref: '#/components/schemas/MaterialType' },
        url: { type: 'string' },
      },
    },
    CaptchaData: {
      type: 'object',
      required: ['captchaId', 'imageBase64'],
      properties: {
        captchaId: { type: 'string', format: 'uuid' },
        imageBase64: {
          type: 'string',
          description: 'data:image/png;base64,...；PNG 固定 120×48 像素，可直接作 img src',
        },
      },
    },
    LoginRequest: {
      type: 'object',
      required: ['userAccount', 'password', 'captchaId', 'captchaCode'],
      properties: {
        userAccount: { type: 'string' },
        password: {
          type: 'string',
          description: 'AES-256-GCM：IV(12)+ciphertext+tag → Base64',
        },
        captchaId: { type: 'string', format: 'uuid' },
        captchaCode: { type: 'string' },
      },
    },
    LoginPendingData: {
      type: 'object',
      required: ['state', 'expiresIn'],
      properties: {
        state: { type: 'string', description: '微信 qrconnect state' },
        expiresIn: { type: 'integer', description: '秒；建议 120～180' },
      },
    },
    LoginQueryData: {
      type: 'object',
      properties: {
        status: {
          type: 'integer',
          enum: [0, 1, 2, 3],
          description:
            '0 待扫码 / 1 成功 / 2 失败 / 3 已过期。0 继续轮询；成功时本响应消费 state',
        },
        errorMsg: { type: 'string' },
        accessToken: { type: 'string' },
        expiresIn: { type: 'integer', description: 'access 秒数，默认 7200' },
        refreshToken: { type: 'string' },
        refreshExpiresIn: {
          type: 'integer',
          description: 'refresh 秒数，默认 604800',
        },
        user: { $ref: '#/components/schemas/EmployeeBrief' },
      },
    },
    RefreshRequest: {
      type: 'object',
      required: ['accessToken', 'refreshToken'],
      properties: {
        accessToken: { type: 'string', description: '旧 access，过期也要原样带上' },
        refreshToken: { type: 'string' },
      },
    },
    TokenPairData: {
      type: 'object',
      required: ['accessToken', 'expiresIn', 'refreshToken', 'refreshExpiresIn'],
      properties: {
        accessToken: { type: 'string' },
        expiresIn: { type: 'integer', example: 7200 },
        refreshToken: { type: 'string' },
        refreshExpiresIn: { type: 'integer', example: 604800 },
      },
    },
    UploadImageData: {
      type: 'object',
      required: ['url'],
      properties: {
        url: { type: 'string', format: 'uri', description: '微吼 domain_url' },
      },
    },
    TosStsData: {
      type: 'object',
      required: [
        'region',
        'endpoint',
        'bucketName',
        'accessKeyId',
        'secretAccessKey',
        'sessionToken',
        'expiredTime',
      ],
      properties: {
        region: { type: 'string', example: 'cn-shanghai' },
        endpoint: {
          type: 'string',
          example: 'tos-cn-shanghai.volces.com',
          description: '主机名，无 https://',
        },
        bucketName: { type: 'string' },
        accessKeyId: { type: 'string' },
        secretAccessKey: { type: 'string' },
        sessionToken: { type: 'string' },
        expiredTime: {
          type: 'string',
          description: 'ISO-8601 UTC（带 Z）',
          example: '2026-10-10T06:00:00Z',
        },
      },
    },
    MaterialGroup: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        name: { type: 'string', maxLength: 64 },
        count: { type: 'integer' },
        isDefault: { type: 'boolean' },
      },
    },
    MaterialItem: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        type: { $ref: '#/components/schemas/MaterialType' },
        groupId: { $ref: '#/components/schemas/BusinessUuid' },
        name: { type: 'string' },
        url: { type: 'string', description: '文章省略' },
        objectKey: { type: 'string', description: '文章省略' },
        thumbUrl: { type: 'string' },
        meta: { type: 'string' },
        fileSize: { type: 'integer', format: 'int64' },
        status: { type: 'string', example: 'available' },
        aiAuditStatus: { $ref: '#/components/schemas/MaterialAuditStatus' },
        riskAuditStatus: { $ref: '#/components/schemas/MaterialAuditStatus' },
        uploaderId: {
          $ref: '#/components/schemas/EmployeeId',
          description: '上传人 rs_mainkey_id',
        },
        uploaderName: { type: 'string', description: '上传人姓名快照' },
        departmentName: { type: 'string', description: '上传人部门快照' },
        uploadedTime: { type: 'string', example: '2026-10-12 14:00:00' },
        vhallVideoId: { type: 'string', description: '仅 video；微吼 video_id' },
        vhallTranscodeProgress: {
          type: 'integer',
          minimum: 0,
          maximum: 100,
          description: '仅 video；转码进度 0～100',
        },
        vhallTranscodeStatus: {
          type: 'integer',
          enum: [0, 1, 3],
          description: '0 转码中 / 1 成功 / 3 失败（仅 video）',
        },
      },
    },
    MaterialListData: {
      type: 'object',
      properties: {
        total: { type: 'integer', format: 'int64' },
        page: { type: 'integer' },
        pageSize: { type: 'integer' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/MaterialItem' },
        },
      },
    },
    MaterialRegisterRequest: {
      type: 'object',
      required: ['type', 'groupId', 'items'],
      properties: {
        type: { $ref: '#/components/schemas/MaterialFileType' },
        groupId: { $ref: '#/components/schemas/BusinessUuid' },
        items: {
          type: 'array',
          minItems: 1,
          maxItems: 100,
          items: {
            type: 'object',
            required: ['name', 'url', 'objectKey'],
            properties: {
              name: { type: 'string', maxLength: 256 },
              url: { type: 'string', maxLength: 2048 },
              objectKey: { type: 'string', maxLength: 1024 },
              fileSize: { type: 'integer', minimum: 0 },
              mimeType: { type: 'string', maxLength: 128 },
              meta: { type: 'string', maxLength: 128 },
            },
          },
        },
      },
    },
    MaterialRegisterData: {
      type: 'object',
      properties: {
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/MaterialItem' },
        },
      },
    },
    ArticleCreateRequest: {
      type: 'object',
      required: ['groupId', 'title', 'author', 'content'],
      properties: {
        groupId: { $ref: '#/components/schemas/BusinessUuid' },
        title: { type: 'string', maxLength: 45 },
        author: { type: 'string', maxLength: 16 },
        content: { type: 'string', maxLength: 100000 },
      },
    },
    ArticleUpdateRequest: {
      type: 'object',
      required: ['title', 'author', 'content'],
      properties: {
        title: { type: 'string', maxLength: 45 },
        author: { type: 'string', maxLength: 16 },
        content: { type: 'string', maxLength: 100000 },
      },
    },
    ArticleDetail: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        groupId: { $ref: '#/components/schemas/BusinessUuid' },
        title: { type: 'string', maxLength: 45 },
        author: { type: 'string', maxLength: 16 },
        content: { type: 'string', maxLength: 100000 },
        aiAuditStatus: { $ref: '#/components/schemas/MaterialAuditStatus' },
        riskAuditStatus: { $ref: '#/components/schemas/MaterialAuditStatus' },
      },
    },
    CreateActivityRequest: {
      type: 'object',
      required: [
        'provider',
        'title',
        'startTime',
        'type',
        'listed',
        'hostIds',
        'reportId',
      ],
      properties: {
        provider: { $ref: '#/components/schemas/Provider' },
        title: { type: 'string', maxLength: 64 },
        startTime: {
          type: 'string',
          description: 'yyyy-MM-dd HH:mm:ss',
          example: '2026-10-01 20:00:00',
        },
        endTime: { type: 'string', description: '须晚于 startTime；只落本站库' },
        type: { $ref: '#/components/schemas/ActivityType' },
        recordId: {
          type: 'integer',
          format: 'int64',
          description:
            'type=4 且 provider=vhall：微吼回放资源 ID（微吼 integer / JSON number），直接作 create-demand 的 record_id。与 GET .../records 的 list[].id 同型。与 recordMaterialId 二选一，不可同时传；本站不落库',
        },
        recordMaterialId: {
          $ref: '#/components/schemas/BusinessUuid',
          description:
            'type=4：本站视频素材 UUID。请求/响应/库列统一：JSON recordMaterialId ↔ 库 record_material_id。须未软删、video、两审通过、有 url。与 recordId 二选一；库：VARCHAR(36)',
        },
        isTiming: {
          type: 'boolean',
          description:
            '仅 type=4：是否定时 → 微吼 is_timing（true→1，false→0）。省略按 true。落本站 activity.is_timing；库：TINYINT(1)',
        },
        introduction: { type: 'string', maxLength: 256 },
        viewerStyle: { $ref: '#/components/schemas/ViewerStyle' },
        ultraLowLatency: {
          type: 'boolean',
          description:
            '是否超低延迟。true 时创建微吼传 no_delay_webinar=1，false 传 0；不传则不带该参。落本站库。创建后不可改',
        },
        coverUrl: { type: 'string' },
        detailHtml: { type: 'string', maxLength: 100000 },
        listed: {
          type: 'boolean',
          description: '是否上架；true→on，false→pending；创建 riskStatus=passed（D109）',
        },
        hostIds: {
          type: 'array',
          minItems: 1,
          maxItems: 20,
          items: { $ref: '#/components/schemas/EmployeeId' },
          description: '主播员工 id（写入 activity_member role=1）',
        },
        ownerIds: {
          type: 'array',
          maxItems: 20,
          items: { $ref: '#/components/schemas/EmployeeId' },
          description: '归属人员工 id（activity_owner）；省略或 [] 表示无',
        },
        products: {
          type: 'array',
          maxItems: 20,
          items: { $ref: '#/components/schemas/ProductRef' },
        },
        coursewareMaterialIds: {
          type: 'array',
          maxLength: 100,
          maxItems: 100,
          items: { $ref: '#/components/schemas/BusinessUuid' },
        },
        reportId: {
          $ref: '#/components/schemas/BusinessUuid',
          description:
            '创建必填。绑定已通过直播报备 id（D74）。须 status=passed、未软删；hostIds 须恰好为该报备主播 1 人。创建后不可改绑',
        },
      },
    },
    UpdateActivityRequest: {
      type: 'object',
      description: '至少填一个字段；未出现的关联字段不改',
      properties: {
        title: { type: 'string', maxLength: 64 },
        startTime: { type: 'string' },
        introduction: { type: 'string', maxLength: 256 },
        endTime: { type: 'string' },
        coverUrl: { type: 'string' },
        detailHtml: { type: 'string', maxLength: 100000 },
        listed: { type: 'boolean', description: '是否上架；保存立刻改 shelfStatus' },
        hostIds: {
          type: 'array',
          minItems: 1,
          maxItems: 20,
          items: { $ref: '#/components/schemas/EmployeeId' },
          description: '全量覆盖主播（role=1）；不删助理/嘉宾',
        },
        ownerIds: {
          type: 'array',
          maxItems: 20,
          items: { $ref: '#/components/schemas/EmployeeId' },
          description: '全量覆盖归属人',
        },
        products: {
          type: 'array',
          maxItems: 20,
          items: { $ref: '#/components/schemas/ProductRef' },
        },
        coursewareMaterialIds: {
          type: 'array',
          maxItems: 100,
          items: { $ref: '#/components/schemas/BusinessUuid' },
        },
        recordMaterialId: {
          $ref: '#/components/schemas/BusinessUuid',
          description:
            '仅 type=4：替换录播素材（与创建/详情同名 recordMaterialId）。写入后 ready→0（D74）',
        },
      },
    },
    PlaybackSettingsRequest: {
      type: 'object',
      required: ['playbackEnabled'],
      properties: {
        playbackEnabled: { type: 'boolean' },
        playbackValidityType: {
          $ref: '#/components/schemas/PlaybackValidityType',
        },
        playbackValidityDays: { type: 'integer', minimum: 1 },
        playbackSpeedAllow: { type: 'boolean' },
        playbackFastForwardAllow: { type: 'boolean' },
      },
    },
    SalesSettingsRequest: {
      type: 'object',
      required: ['sellAlone', 'sellType'],
      properties: {
        sellAlone: {
          type: 'boolean',
          description:
            '是否单独售卖。为 false 时仍接受并保存下方字段（前端可禁用编辑，回显用）',
        },
        sellType: { $ref: '#/components/schemas/SellType' },
        price: {
          type: 'number',
          multipleOf: 0.01,
          description:
            '历史付费字段；PUT 保存时一律清空，勿再依赖。元，最多 2 位小数',
        },
        linePrice: {
          type: 'number',
          multipleOf: 0.01,
          description: '历史划线价；PUT 保存时一律清空，勿再依赖',
        },
        password: {
          type: 'string',
          pattern: '^\\d{6}$',
          description:
            'sellType=encrypted 时必填，恰好 6 位数字明文；服务端 AES 加密落库。非加密时忽略并清空密文',
        },
        accessValidityType: {
          $ref: '#/components/schemas/AccessValidityType',
          description:
            '访问有效期类型。sellType=free 时服务端强制 long；非 free 时必填 long / days（直播无 fixed）',
        },
        accessValidityDays: {
          type: 'integer',
          minimum: 1,
          description:
            'accessValidityType=days 时必填，整数 ≥1；表示直播实际结束后 N 天内可访问',
        },
      },
    },
    SalesSettingsData: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        sellType: { $ref: '#/components/schemas/SellType' },
        validityLabel: {
          type: 'string',
          description:
            '售卖有效期展示文案（activity.validity_label）。示例：长期有效 / 7天有效',
        },
      },
    },
    ShelfRequest: {
      type: 'object',
      required: ['listed'],
      properties: {
        listed: { type: 'boolean', description: 'true 上架 / false 下架' },
      },
    },
    ShelfData: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        shelfStatus: { $ref: '#/components/schemas/ShelfStatus' },
      },
    },
    AuditSubmitRequest: {
      type: 'object',
      required: ['result'],
      properties: {
        result: { type: 'string', enum: ['passed', 'rejected'] },
        reason: {
          type: 'string',
          minLength: 1,
          maxLength: 200,
          description: '仅驳回必填',
        },
      },
    },
    AuditSubmitData: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        riskStatus: { $ref: '#/components/schemas/RiskStatus' },
        shelfStatus: { $ref: '#/components/schemas/ShelfStatus' },
      },
    },
    AuditRecord: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        action: {
          type: 'string',
          enum: ['submit', 'audit'],
          description:
            'submit 发起申请 / audit 通过或驳回。发起申请不返回 result、reason',
        },
        result: {
          type: 'string',
          enum: ['passed', 'rejected'],
          description: 'passed 已通过 / rejected 已驳回。仅 action=audit 时返回',
        },
        reason: { type: 'string', description: '仅驳回时返回' },
        operatorId: {
          $ref: '#/components/schemas/EmployeeId',
          description: '操作人员工 id',
        },
        operatorName: { type: 'string', description: '操作人姓名' },
        createdTime: { type: 'string' },
      },
    },
    ActivitySummary: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        provider: { $ref: '#/components/schemas/Provider' },
        title: { type: 'string' },
        startTime: { type: 'string' },
        coverUrl: { type: 'string' },
        state: { $ref: '#/components/schemas/ActivityState' },
        type: { $ref: '#/components/schemas/ActivityType' },
        viewerStyle: { $ref: '#/components/schemas/ViewerStyle' },
        createdTime: { type: 'string' },
        shelfStatus: { $ref: '#/components/schemas/ShelfStatus' },
        riskStatus: { $ref: '#/components/schemas/RiskStatus' },
        sellType: { $ref: '#/components/schemas/SellType' },
        validityLabel: { type: 'string' },
        canBroadcast: { type: 'boolean' },
        ready: {
          type: 'integer',
          enum: [0, 1, 2],
          description: '1 就绪（默认）/ 0 转码中 / 2 失败不可重试；库：INT',
        },
        recordMaterialId: {
          $ref: '#/components/schemas/BusinessUuid',
          description:
            '本场绑定的录播素材 id（D74，与创建/改活动请求同名）；非录播或不绑可不返回',
        },
        owners: {
          type: 'array',
          items: { $ref: '#/components/schemas/EmployeeBrief' },
        },
        products: {
          type: 'array',
          items: { $ref: '#/components/schemas/ProductRef' },
        },
      },
    },
    ActivityDetail: {
      allOf: [
        { $ref: '#/components/schemas/ActivitySummary' },
        {
          type: 'object',
          properties: {
            providerRoomId: { type: 'string' },
            introduction: { type: 'string' },
            endTime: { type: 'string' },
            verify: {
              type: 'integer',
              enum: [0, 1, 2, 3, 4, 6],
              description:
                '0 无验证 / 1 密码 / 2 白名单 / 3 付费 / 4 F码 / 6 付费+F码',
            },
            pv: { type: 'integer' },
            ultraLowLatency: { type: 'boolean' },
            isTiming: {
              type: 'boolean',
              description: '录播是否定时；非录播可空；库：TINYINT(1)',
            },
            detailHtml: { type: 'string' },

            reportId: {
              $ref: '#/components/schemas/BusinessUuid',
              description: '绑定的直播报备；未绑定不输出',
            },
            reportTitle: {
              type: 'string',
              description: '报备标题；未绑定不输出',
            },
            hosts: {
              type: 'array',
              items: { $ref: '#/components/schemas/EmployeeBrief' },
            },
            coursewares: {
              type: 'array',
              items: { $ref: '#/components/schemas/CoursewareItem' },
            },
            sellAlone: { type: 'boolean' },
            price: { type: 'number', description: '售价（元）；非付费可不返回' },
            linePrice: {
              type: 'number',
              description: '划线价（元）；无则可不返回',
            },
            accessValidityType: {
              $ref: '#/components/schemas/AccessValidityType',
            },
            accessValidityDays: { type: 'integer' },
            password: {
              type: 'string',
              description: '仅 encrypted 时回显明文；勿给观看端',
            },
            playbackEnabled: { type: 'boolean' },
            playbackValidityType: {
              $ref: '#/components/schemas/PlaybackValidityType',
            },
            playbackValidityDays: { type: 'integer' },
            playbackSpeedAllow: { type: 'boolean' },
            playbackFastForwardAllow: { type: 'boolean' },
          },
        },
      ],
    },
    ActivityListData: {
      type: 'object',
      properties: {
        total: { type: 'integer', format: 'int64' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/ActivitySummary' },
        },
      },
    },
    ActivityRecordItem: {
      type: 'object',
      required: ['id'],
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: 'create-demand 的 record_id（微吼 integer / JSON number）',
          example: 799703,
        },
        title: { type: 'string' },
        duration: { type: 'string', example: '01:02:03' },
        createdTime: { type: 'string' },
        coverUrl: { type: 'string' },
        storage: { type: 'string', description: '体积 KB 字符串' },
        status: {
          type: 'integer',
          enum: [1, 2, 3, 4, 5],
          description:
            '1 待打包 / 2 打包中 / 3 下载中 / 4 就绪 / 5 失败；库：INT',
        },
        tosUrl: { type: 'string', description: '本站 TOS/CDN URL；无则省略' },
        downloadUrl: {
          type: 'string',
          description: '仅 status=4（ready）时与 tosUrl 同值；否则省略',
        },
      },
    },
    ActivityRecordListData: {
      type: 'object',
      properties: {
        total: { type: 'integer', format: 'int64' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/ActivityRecordItem' },
        },
      },
    },
    ActivityRecordDownloadData: {
      type: 'object',
      required: ['recordId', 'packStatus', 'progress'],
      description:
        '回放下载：按本库状态推进 TOS；对应管理后台回放列表「下载」（D88）',
      properties: {
        recordId: {
          type: 'integer',
          format: 'int64',
          description: '云侧回放 id（同 GET .../records 的 list[].id）',
          example: 5814669,
        },
        tosUrl: {
          type: 'string',
          format: 'uri',
          description: '本站 TOS/CDN；有值时前端直接下载',
        },
        downloadUrl: {
          type: 'string',
          format: 'uri',
          description: '与 tosUrl 同值（兼容旧前端）；无 TOS 时省略',
        },
        packStatus: {
          type: 'integer',
          description: '0 未开始 / 1 可下载 / 2 处理中 / 3 失败',
          enum: [0, 1, 2, 3],
          example: 1,
        },
        progress: {
          type: 'integer',
          description: '打包进度 0～100',
          minimum: 0,
          maximum: 100,
          example: 100,
        },
        downloadStartedTime: {
          type: 'string',
          description:
            'packStatus=2 且本站 JVM 确有 ingest 任务时返回；否则省略',
        },
      },
    },
    EndActivityData: {
      type: 'object',
      required: ['id', 'state'],
      description:
        '结束直播 / 第三方推流开始直播（/activities/start）共用：返回本站活动 id 与写回后的 state',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid', description: '本站活动 id（D74）' },
        state: {
          $ref: '#/components/schemas/ActivityState',
          description: '本站写回后的活动状态；结束多为 3，start 成功多为 1',
        },
      },
    },
    ActivityMemberItem: {
      type: 'object',
      required: ['id', 'userName', 'role', 'nickname', 'tag'],
      properties: {
        id: { $ref: '#/components/schemas/EmployeeId' },
        userName: {
          type: 'string',
          description: '员工姓名（实时查员工表，不落成员表）',
        },
        headimgurl: {
          type: 'string',
          description:
            '员工微信头像（实时查 sys_useraccount.headimgurl，不落成员表）；空则不返回。本场头像仍是 avatarUrl',
        },
        departmentName: {
          type: 'string',
          description: '任职部门展示名；无则 "" 或不返回',
        },
        role: { $ref: '#/components/schemas/ActivityMemberRole' },
        nickname: {
          type: 'string',
          description: '本场昵称；库空时回 userName',
        },
        avatarUrl: {
          type: 'string',
          description: '本场头像；库空时可不返回',
        },
        tag: {
          type: 'string',
          description: '标签；库空时回 ""',
        },
      },
    },
    ActivityMemberListData: {
      type: 'object',
      required: ['list'],
      properties: {
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/ActivityMemberItem' },
          description: '按 role 升序，同角色内按 sort_order 升序',
        },
      },
    },
    UpdateActivityMemberRequest: {
      type: 'object',
      required: ['id'],
      description:
        '只改一名已有成员的本场昵称、头像、标签。不改 role，不增删其他人',
      properties: {
        id: { $ref: '#/components/schemas/EmployeeId' },
        nickname: {
          type: 'string',
          maxLength: 10,
          description:
            '本场昵称 ≤10；有值用请求值；省略或空 → 快照员工 user_name（超长截断至 10）',
        },
        avatarUrl: {
          type: 'string',
          maxLength: 512,
          description:
            '本场头像 ≤512；有值用请求值；省略或空 → 快照员工头像（headimgurl 优先，其次 user_avatar）',
        },
        tag: {
          type: 'string',
          maxLength: 32,
          description: '标签 ≤32；省略或空 → 该角色默认标签',
        },
      },
    },
    AddActivityMembersRequest: {
      type: 'object',
      required: ['members'],
      description: '只插入这些人，不删除、不修改已有成员',
      properties: {
        members: {
          type: 'array',
          minItems: 1,
          description:
            '本次要新增的人，至少 1 人，不是整份名单。同一 id 不可重复，且不能已是本场成员。加上已有人数后每个角色仍不超过 20',
          items: {
            type: 'object',
            required: ['id', 'role'],
            properties: {
              id: { $ref: '#/components/schemas/EmployeeId' },
              role: { $ref: '#/components/schemas/ActivityMemberRole' },
              nickname: {
                type: 'string',
                maxLength: 10,
                description: '本场昵称 ≤10；省略或空 → 快照员工 user_name',
              },
              avatarUrl: {
                type: 'string',
                maxLength: 512,
                description: '本场头像 ≤512；省略或空 → 快照员工头像',
              },
              tag: {
                type: 'string',
                maxLength: 32,
                description: '标签 ≤32；省略或空 → 该角色默认标签',
              },
            },
          },
        },
      },
    },
    DeleteActivityMembersRequest: {
      type: 'object',
      required: ['ids'],
      description: '只删除列出的员工，其余成员不动',
      properties: {
        ids: {
          type: 'array',
          minItems: 1,
          description:
            '要删除的员工 rs_mainkey_id，至少 1 个。同一 id 不能重复；不是本场成员 → 400。删完至少留 1 名主播',
          items: { $ref: '#/components/schemas/EmployeeId' },
        },
      },
    },
    AcceptActivityMemberRequest: {
      type: 'object',
      required: ['role', 'inviterId'],
      properties: {
        role: { $ref: '#/components/schemas/ActivityMemberRole' },
        inviterId: { $ref: '#/components/schemas/EmployeeId' },
      },
    },
    AcceptActivityMemberData: {
      type: 'object',
      required: ['id', 'userName', 'role', 'alreadyMember'],
      properties: {
        id: { $ref: '#/components/schemas/EmployeeId' },
        userName: { type: 'string', description: '员工姓名' },
        role: { $ref: '#/components/schemas/ActivityMemberRole' },
        alreadyMember: {
          type: 'boolean',
          description: '打开前已经是该角色则为 true',
        },
        inviterName: { type: 'string', description: '邀约人姓名' },
      },
    },
    HostData: {
      type: 'object',
      required: ['provider', 'hostUrl', 'role'],
      properties: {
        provider: { $ref: '#/components/schemas/Provider' },
        hostUrl: {
          type: 'string',
          format: 'uri',
          description:
            '微吼 page_url；本站 /live/studio/{id}?role= 页的 iframe src；勿给观众',
        },
        role: { $ref: '#/components/schemas/ActivityMemberRole' },
      },
    },
    PushStreamData: {
      type: 'object',
      required: ['streamAddress', 'streamCode'],
      properties: {
        streamAddress: {
          type: 'string',
          description: '推流服务器，对应微吼 stream_address',
          example: 'rtmp://rslive.vhall.com:1935/vhallyun/',
        },
        streamCode: {
          type: 'string',
          description: '串流密钥，对应微吼 stream_code',
        },
        expireTime: {
          type: 'string',
          description: '有效期，对应微吼 expire_time；没有则不返回',
          example: '2026-02-15 00:00:01',
        },
      },
    },
    StreamStatusData: {
      type: 'object',
      required: ['status'],
      properties: {
        status: {
          type: 'integer',
          description: '是否有流：0 无，1 有。对应微吼 data.status',
          enum: [0, 1],
          example: 0,
        },
      },
    },
    ActivityStats: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        title: { type: 'string' },
        state: { $ref: '#/components/schemas/ActivityState' },
        type: { $ref: '#/components/schemas/ActivityType' },
        pv: { type: 'integer' },
        onlineCount: { type: 'integer' },
      },
    },
    OnlinePoint: {
      type: 'object',
      properties: {
        time: { type: 'string' },
        count: { type: 'integer' },
      },
    },
    OnlineTrend: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        startTime: { type: 'string' },
        endTime: { type: 'string' },
        points: {
          type: 'array',
          items: { $ref: '#/components/schemas/OnlinePoint' },
        },
      },
    },
    PresenceTrendPoint: {
      type: 'object',
      properties: {
        time: { type: 'string' },
        online: { type: 'integer' },
        enter: { type: 'integer' },
        leave: { type: 'integer' },
      },
    },
    PresenceTrend: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        startTime: { type: 'string' },
        endTime: { type: 'string' },
        bucketSeconds: { type: 'integer', example: 60 },
        points: {
          type: 'array',
          items: { $ref: '#/components/schemas/PresenceTrendPoint' },
        },
      },
    },
    AttendeeItem: {
      type: 'object',
      required: ['userId', 'enterTime', 'online'],
      properties: {
        userId: { type: 'string' },
        nickname: { type: 'string' },
        avatarUrl: { type: 'string' },
        enterTime: { type: 'string' },
        leaveTime: { type: 'string' },
        online: { type: 'boolean' },
      },
    },
    AttendeeListData: {
      type: 'object',
      required: ['activityState', 'total', 'page', 'pageSize', 'list'],
      properties: {
        activityState: { $ref: '#/components/schemas/ActivityState' },
        total: { type: 'integer', format: 'int64' },
        page: { type: 'integer' },
        pageSize: { type: 'integer' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/AttendeeItem' },
        },
      },
    },
    OrgDeptNode: {
      type: 'object',
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: '部门 rs_mainkey_id（JSON 数字，D56）',
        },
        name: { type: 'string', description: '部门名称 bmmc' },
        code: { type: 'string', description: '部门编码 bmbm' },
        parentId: {
          type: 'integer',
          format: 'int64',
          description: '上级部门 id；根为 0',
        },
        children: {
          type: 'array',
          items: { $ref: '#/components/schemas/OrgDeptNode' },
        },
      },
    },
    OrgEmployeeItem: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/EmployeeId' },
        userName: { type: 'string', description: '员工姓名' },
        departmentName: { type: 'string', description: '部门展示名' },
        roleNames: { type: 'string', description: '角色名，多角色顿号连接' },
        liveEnabled: { type: 'boolean', description: '本站直播账号是否启用' },
        hqStatus: {
          type: 'string',
          enum: ['normal', 'disabled', 'unknown'],
          description: 'normal 总部正常 / disabled 总部禁用 / unknown 未知',
        },
      },
    },
    OrgEmployeeListData: {
      type: 'object',
      properties: {
        page: { type: 'integer' },
        pageSize: { type: 'integer' },
        total: { type: 'integer', format: 'int64' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/OrgEmployeeItem' },
        },
      },
    },
    LiveEnabledRequest: {
      type: 'object',
      required: ['enabled'],
      properties: { enabled: { type: 'boolean' } },
    },
    LiveEnabledData: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/EmployeeId' },
        liveEnabled: { type: 'boolean', description: '更新后的启停状态' },
      },
    },
    OrgRoleBrief: {
      type: 'object',
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: '角色 sys_uimsrole.rs_mainkey_id（JSON 数字，D56）',
        },
        name: { type: 'string', description: '角色名称' },
      },
    },
    OrgRoleDetail: {
      type: 'object',
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: '角色 rs_mainkey_id',
        },
        name: { type: 'string' },
        remark: { type: 'string' },
        members: {
          type: 'array',
          items: {
            type: 'object',
            properties: {
              id: { $ref: '#/components/schemas/EmployeeId' },
              userName: { type: 'string' },
              departmentName: { type: 'string' },
            },
          },
        },
        menus: { type: 'array', items: { type: 'object' } },
        menuCodes: { type: 'array', items: { type: 'string' } },
      },
    },
    RoleMenusRequest: {
      type: 'object',
      required: ['id', 'menuCodes'],
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: '角色 rs_mainkey_id（JSON 数字，D56）',
        },
        menuCodes: {
          type: 'array',
          items: { type: 'string' },
          example: ['live-manage'],
        },
      },
    },
    LiveReportStatus: {
      type: 'string',
      enum: ['pending', 'passed', 'rejected'],
      description:
        '报备审核状态：pending 待审核 / passed 已通过 / rejected 已驳回',
    },
    LiveReportPlanTime: {
      type: 'object',
      required: ['startTime', 'endTime'],
      properties: {
        startTime: { type: 'string', example: '2026-10-20 20:00:00' },
        endTime: {
          type: 'string',
          description: '须晚于开始；日期不得晚于服务端当天加 7 天',
        },
      },
    },
    LiveReportAttachment: {
      type: 'object',
      required: ['name', 'url', 'objectKey', 'fileSize'],
      properties: {
        name: { type: 'string' },
        url: { type: 'string' },
        objectKey: {
          type: 'string',
          description: '新建须以 live-report/attachment/ 开头；驳回后重提交还接受 live-report/{id}/attachment/。仅 doc/docx/pdf，每份 ≤10MB',
        },
        fileSize: { type: 'integer', format: 'int64' },
        mimeType: { type: 'string' },
      },
    },
    LiveReportHost: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/EmployeeId' },
        userName: { type: 'string', description: '主播姓名' },
        avatar: { type: 'string', description: '主播头像；没有则不返回' },
      },
    },
    LiveReportSaveRequest: {
      type: 'object',
      required: [
        'title',
        'content',
        'planTimes',
        'coverUrl',
        'coverObjectKey',
        'hostId',
        'attachments',
      ],
      properties: {
        title: { type: 'string', maxLength: 64 },
        content: { type: 'string', maxLength: 500 },
        planTimes: {
          type: 'array',
          minItems: 1,
          maxItems: 10,
          items: { $ref: '#/components/schemas/LiveReportPlanTime' },
        },
        coverUrl: { type: 'string' },
        coverObjectKey: {
          type: 'string',
          description: 'material/image/ 开头时必须带 coverMaterialId',
        },
        coverFileSize: { type: 'integer', format: 'int64' },
        coverMimeType: { type: 'string' },
        coverMaterialId: { $ref: '#/components/schemas/BusinessUuid' },
        hostId: {
          $ref: '#/components/schemas/EmployeeId',
          description: '主播员工 id',
        },
        attachments: {
          type: 'array',
          minItems: 1,
          maxItems: 20,
          items: { $ref: '#/components/schemas/LiveReportAttachment' },
        },
      },
    },
    LiveReportUpdateRequest: {
      type: 'object',
      description:
        '驳回态提交完整字段并回到 pending。已通过且未绑定直播时只允许 planTimes，且只能在原数组尾部追加',
      properties: {
        title: { type: 'string', maxLength: 64 },
        content: { type: 'string', maxLength: 500 },
        planTimes: {
          type: 'array',
          minItems: 1,
          maxItems: 10,
          items: { $ref: '#/components/schemas/LiveReportPlanTime' },
        },
        coverUrl: { type: 'string' },
        coverObjectKey: { type: 'string' },
        coverFileSize: { type: 'integer', format: 'int64' },
        coverMimeType: { type: 'string' },
        coverMaterialId: { $ref: '#/components/schemas/BusinessUuid' },
        hostId: {
          $ref: '#/components/schemas/EmployeeId',
          description: '主播员工 id',
        },
        attachments: {
          type: 'array',
          minItems: 1,
          maxItems: 20,
          items: { $ref: '#/components/schemas/LiveReportAttachment' },
        },
      },
    },
    LiveReportAuditRequest: {
      type: 'object',
      required: ['result'],
      properties: {
        result: { type: 'string', enum: ['passed', 'rejected'] },
        reason: {
          type: 'string',
          maxLength: 500,
          description: '驳回必填，去空白后 1～500 字',
        },
      },
    },
    LiveReportResult: {
      type: 'object',
      required: ['id', 'status'],
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        status: { $ref: '#/components/schemas/LiveReportStatus' },
      },
    },
    LiveReportDetail: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        title: { type: 'string' },
        content: { type: 'string' },
        planTimes: {
          type: 'array',
          items: { $ref: '#/components/schemas/LiveReportPlanTime' },
        },
        coverUrl: { type: 'string' },
        coverObjectKey: { type: 'string' },
        coverFileSize: { type: 'integer', format: 'int64' },
        coverMimeType: { type: 'string' },
        coverMaterialId: { $ref: '#/components/schemas/BusinessUuid' },
        host: { $ref: '#/components/schemas/LiveReportHost' },
        attachments: {
          type: 'array',
          description: '列表项可省略',
          items: { $ref: '#/components/schemas/LiveReportAttachment' },
        },
        status: { $ref: '#/components/schemas/LiveReportStatus' },
        boundLiveCount: { type: 'integer' },
        submitterId: {
          $ref: '#/components/schemas/EmployeeId',
          description: '提交人员工 id',
        },
        submitterName: { type: 'string', description: '提交人姓名快照' },
        departmentName: { type: 'string', description: '提交人部门快照' },
        submittedTime: { type: 'string' },
        updatedTime: { type: 'string' },
      },
    },
    LiveReportListData: {
      type: 'object',
      properties: {
        page: { type: 'integer' },
        pageSize: { type: 'integer' },
        total: { type: 'integer', format: 'int64' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/LiveReportDetail' },
        },
      },
    },
    LiveReportAuditItem: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        action: {
          type: 'string',
          enum: ['submit', 'resubmit', 'audit', 'append'],
        },
        result: { type: 'string', enum: ['passed', 'rejected'] },
        reason: { type: 'string' },
        operatorId: {
          $ref: '#/components/schemas/EmployeeId',
          description: '操作人员工 id',
        },
        operatorName: { type: 'string', description: '操作人姓名' },
        createdTime: { type: 'string' },
      },
    },
    LiveReportAuditList: {
      type: 'object',
      properties: {
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/LiveReportAuditItem' },
        },
      },
    },
    CoursePerson: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/EmployeeId' },
        userName: { type: 'string', description: '员工姓名' },
        avatar: { type: 'string', description: '头像；没有则不返回' },
      },
    },
    CourseMaterial: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        name: { type: 'string' },
        url: { type: 'string' },
        type: { $ref: '#/components/schemas/MaterialFileType' },
        fileSize: { type: 'integer', format: 'int64' },
        mimeType: { type: 'string' },
        accessPermission: {
          $ref: '#/components/schemas/MaterialAccessPermission',
        },
      },
    },
    CourseMaterialInput: {
      type: 'object',
      required: ['id'],
      properties: {
        id: {
          $ref: '#/components/schemas/BusinessUuid',
          description: '课件素材 UUID（D74）',
        },
        accessPermission: {
          $ref: '#/components/schemas/MaterialAccessPermission',
          description:
            '选填；压缩包扩展名固定 3；可预览格式默认 1，仅允许 1/2',
        },
      },
    },
    CreateCourseRequest: {
      type: 'object',
      required: ['title', 'coverMaterialId', 'listed', 'ownerIds'],
      properties: {
        title: { type: 'string', maxLength: 45 },
        introduction: { type: 'string', maxLength: 256 },
        detailHtml: {
          type: 'string',
          maxLength: 200000,
          description: '可见文字 ≤10000，原文 ≤200000',
        },
        coverMaterialId: {
          $ref: '#/components/schemas/BusinessUuid',
          description: '两审通过的 JPG/PNG 图片素材，≤5MB（D74）',
        },
        listed: { type: 'boolean', description: '是否上架（D109）' },
        ownerIds: {
          type: 'array',
          minItems: 1,
          maxItems: 20,
          items: { $ref: '#/components/schemas/EmployeeId' },
          description: '在职员工 id（JSON 数字，D56）',
        },
        materials: {
          type: 'array',
          maxItems: 100,
          items: { $ref: '#/components/schemas/CourseMaterialInput' },
          description:
            '课件（选填）：有序数组，每项 id + accessPermission；两审通过的 image/video/document，不含 article',
        },
      },
    },
    CourseSalesUpdateRequest: {
      type: 'object',
      required: ['sellAlone', 'sellType'],
      properties: {
        sellAlone: {
          type: 'boolean',
          description:
            '是否单独售卖。为 false 时仍接受并保存下方字段（前端可禁用编辑，回显用）',
        },
        sellType: { $ref: '#/components/schemas/CourseSellType' },
        password: {
          type: 'string',
          pattern: '^\\d{6}$',
          description:
            'sellType=encrypted 时必填，恰好 6 位数字明文；服务端 AES 加密写入 course.sell_password_cipher。非加密时忽略并清空密文',
        },
        accessValidityType: {
          $ref: '#/components/schemas/AccessValidityType',
          description:
            '访问有效期类型。sellType=free 时服务端强制 long；非 free 时必填 long / days / fixed',
        },
        accessValidityDays: {
          type: 'integer',
          minimum: 1,
          description:
            'accessValidityType=days 时必填，整数 ≥1；表示学员权益按 created_time 起算 N 天',
        },
        accessValidityFixedTime: {
          type: 'string',
          description:
            'accessValidityType=fixed 时必填；课程统一到期时刻，格式 yyyy-MM-dd HH:mm:ss；库：DATETIME(3)',
        },
      },
    },
    CourseSalesUpdateData: {
      type: 'object',
      required: ['id', 'sellType', 'validityLabel'],
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        sellType: { $ref: '#/components/schemas/CourseSellType' },
        validityLabel: {
          type: 'string',
          description:
            '售卖有效期展示文案（course.validity_label）。示例：长期有效 / 7天有效 / 2026-11-14 00:00:00到期',
        },
      },
    },
    CourseSummary: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        title: { type: 'string' },
        coverUrl: { type: 'string' },
        shelfStatus: { $ref: '#/components/schemas/ShelfStatus' },
        riskStatus: { $ref: '#/components/schemas/RiskStatus' },
        listedTime: {
          type: 'string',
          description:
            '上架时间，yyyy-MM-dd HH:mm:ss；创建/保存勾选上架或列表手动上架时写入；下架/驳回一般不清；无则不返回',
        },
        owners: {
          type: 'array',
          items: { $ref: '#/components/schemas/CoursePerson' },
        },
        contentCount: { type: 'integer', description: '本轮恒为 0' },
        entitledUserCount: { type: 'integer', description: '本轮恒为 0' },
        totalUserCount: { type: 'integer', description: '本轮恒为 0' },
        salesCount: { type: 'integer', description: '本轮恒为 0' },
        updatedTime: { type: 'string' },
        createdTime: { type: 'string' },
        canList: {
          type: 'boolean',
          description: '风控已通过且上架为待上架或已下架',
        },
        canUnlist: { type: 'boolean', description: '已上架' },
        canDelete: {
          type: 'boolean',
          description: '内容数与总销量均为 0（标记位；软删本身不拦）',
        },
        sellType: { $ref: '#/components/schemas/CourseSellType' },
        validityLabel: { type: 'string' },
      },
    },
    CourseDetail: {
      allOf: [
        { $ref: '#/components/schemas/CourseSummary' },
        {
          type: 'object',
          properties: {
            introduction: { type: 'string' },
            detailHtml: { type: 'string' },
            coverMaterialId: { $ref: '#/components/schemas/BusinessUuid' },
            materials: {
              type: 'array',
              items: { $ref: '#/components/schemas/CourseMaterial' },
            },
            sellAlone: { type: 'boolean' },
            accessValidityType: {
              $ref: '#/components/schemas/AccessValidityType',
            },
            accessValidityDays: {
              type: 'integer',
              description: '仅 days 时返回',
            },
            accessValidityFixedTime: {
              type: 'string',
              description: '仅 fixed 时返回，yyyy-MM-dd HH:mm:ss',
            },
            password: {
              type: 'string',
              description:
                '仅 sellType=encrypted 时回显 AES 解密明文（6 位）；禁止写入列表/对外/导出',
            },
          },
        },
      ],
    },
    CourseListData: {
      type: 'object',
      properties: {
        page: { type: 'integer' },
        pageSize: { type: 'integer' },
        total: { type: 'integer', format: 'int64' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/CourseSummary' },
        },
      },
    },
    OutsideCourseListRequest: {
      type: 'object',
      properties: {
        page: {
          type: 'integer',
          default: 1,
          description: '页码，从 1 开始；<1 按 1',
        },
        pageSize: {
          type: 'integer',
          default: 20,
          description: '每页条数；<1 按 20；>100 按 100（不抛 400）',
        },
        courseName: {
          type: 'string',
          description: '课程名称模糊；空或不传则不过滤',
        },
      },
    },
    OutsideCourseItem: {
      type: 'object',
      required: ['courseId', 'courseName', 'contentCount'],
      properties: {
        courseId: {
          $ref: '#/components/schemas/BusinessUuid',
          description: '课程 UUID（D74：32 位无横线）',
        },
        courseName: {
          type: 'string',
          maxLength: 45,
          description: '课程名称；库：VARCHAR(45)',
        },
        contentCount: {
          type: 'integer',
          description: '内容数（读 course.content_count）；库：INT',
        },
        departmentName: {
          type: 'string',
          description:
            '课程部门=归属人（sort_order 最小第一人）员工部门；多部门顿号「、」连接；无则省略',
        },
      },
    },
    OutsideCourseListData: {
      type: 'object',
      required: ['page', 'pageSize', 'total', 'list'],
      properties: {
        page: { type: 'integer' },
        pageSize: { type: 'integer' },
        total: { type: 'integer', format: 'int64' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/OutsideCourseItem' },
        },
      },
    },
    CourseUserListItem: {
      type: 'object',
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: 'course_user.id；库：BIGINT',
        },
        nickname: {
          type: 'string',
          description: '展示昵称（必有非空串）；落在「学员」列',
        },
        avatar: { type: 'string', description: '头像 URL，可空则省略' },
        phoneMasked: { type: 'string', description: '脱敏手机号，可空则省略' },
        tagLabels: {
          type: 'array',
          items: { type: 'string' },
          description: '观众全局标签名（未软删）；无则 []',
        },
        chapterCompletionRate: {
          type: 'number',
          description:
            '小节完成率：已完成小节数 / 当前总小节数，百分数一位小数；无进度为 0',
        },
        entitlementStatus: {
          type: 'integer',
          enum: [0, 1],
          description: '0 已取消 / 1 有效；库：INT',
        },
        entitlementState: {
          $ref: '#/components/schemas/CourseEntitlementState',
        },
        entitlementStateLabel: {
          type: 'string',
          description: '有效 / 已到期 / 已失效',
        },
        acquireWayLabel: { type: 'string', description: '购买 / 导入' },
        firstAcquireTime: {
          type: 'string',
          description: '权益首次获取时间（created_time），yyyy-MM-dd HH:mm:ss',
        },
        expireTime: {
          type: 'string',
          description:
            '库内到期，可空；售卖长期时前端展示「长期」勿当已过期；库：DATETIME(3)',
        },
        expireDisplay: {
          type: 'string',
          description: '展示串：售卖长期→长期；有到期则格式化；无则 —',
        },
      },
    },
    CourseUserListData: {
      type: 'object',
      required: ['page', 'pageSize', 'total', 'list'],
      properties: {
        page: { type: 'integer' },
        pageSize: { type: 'integer' },
        total: { type: 'integer', format: 'int64' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/CourseUserListItem' },
        },
      },
    },
    CourseUserEntitlementRequest: {
      type: 'object',
      required: ['entitlementStatus'],
      properties: {
        entitlementStatus: {
          type: 'integer',
          enum: [0, 1],
          description: '0 已取消 / 1 有效',
        },
      },
    },
    CourseUserEntitlementData: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        entitlementStatus: {
          type: 'integer',
          enum: [0, 1],
          description: '0 已取消 / 1 有效',
        },
      },
    },
    CourseUserExpireRequest: {
      type: 'object',
      required: ['expireTime'],
      properties: {
        expireTime: {
          type: 'string',
          description: '必填，yyyy-MM-dd HH:mm:ss；改 entitlement_expire_time',
        },
      },
    },
    CourseUserExpireData: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        expireTime: { type: 'string', description: 'yyyy-MM-dd HH:mm:ss' },
      },
    },
    CourseUserCreateRequest: {
      type: 'object',
      properties: {
        phone: { type: 'string', description: '与 viewerId 二选一' },
        viewerId: {
          type: 'integer',
          format: 'int64',
          description: '与 phone 二选一',
        },
        expireTime: {
          type: 'string',
          description:
            '选填；仅新建行；yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日则存当日 00:00:00；空则按课程售卖规则计算到期（days → 入表时刻+N 天；fixed → 课程固定时刻；long → 空）',
        },
      },
    },
    CourseUserCreateData: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        viewerId: { type: 'integer', format: 'int64' },
        created: { type: 'boolean', description: 'true 新建；false 已有行' },
      },
    },
    CourseUserImportData: {
      type: 'object',
      properties: {
        importId: { type: 'integer', format: 'int64' },
        total: { type: 'integer' },
        created: { type: 'integer' },
        updated: { type: 'integer' },
        skipped: { type: 'integer' },
        failed: { type: 'integer' },
        errors: {
          type: 'array',
          maxItems: 50,
          items: {
            type: 'object',
            properties: {
              row: { type: 'integer', description: '行号' },
              phone: { type: 'string', description: '手机号' },
              reason: { type: 'string', description: '失败原因' },
            },
          },
        },
      },
    },
    CourseUserImportByTagsRequest: {
      type: 'object',
      required: ['tagIds'],
      properties: {
        tagIds: {
          type: 'array',
          minItems: 1,
          maxItems: 5,
          items: { type: 'integer', format: 'int64' },
          description: '标签 id，去重后 1～5 个；满足其一匹配',
        },
        expireTime: {
          type: 'string',
          description:
            '选填；仅新建行；yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；estimate 无需此字段',
        },
      },
    },
    CourseUserImportByTagsEstimateData: {
      type: 'object',
      required: ['total'],
      properties: {
        total: {
          type: 'integer',
          description: '所选标签满足其一去重后的观众数',
        },
      },
    },
    CourseUserImportByTagsData: {
      type: 'object',
      required: ['total', 'created', 'updated', 'skipped'],
      properties: {
        total: { type: 'integer', description: '命中去重观众数' },
        created: { type: 'integer', description: '新建行数' },
        updated: { type: 'integer', description: '补写导入人数' },
        skipped: { type: 'integer', description: '已有导入人跳过数' },
      },
    },
    CourseUserImportHistoryItem: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        importTime: { type: 'string' },
        importerId: { $ref: '#/components/schemas/EmployeeId' },
        importerName: { type: 'string' },
        successCount: { type: 'integer' },
        failCount: { type: 'integer' },
      },
    },
    CourseUserImportHistoryData: {
      type: 'object',
      properties: {
        list: {
          type: 'array',
          maxItems: 100,
          items: { $ref: '#/components/schemas/CourseUserImportHistoryItem' },
        },
      },
    },
    ActivityAudienceImportByTagsRequest: {
      type: 'object',
      required: ['tagIds'],
      properties: {
        tagIds: {
          type: 'array',
          minItems: 1,
          maxItems: 5,
          items: { type: 'integer', format: 'int64' },
          description: '标签 id，去重后 1～5 个；满足其一匹配',
        },
      },
    },
    ActivityAudienceImportByTagsEstimateData: {
      type: 'object',
      required: ['total'],
      properties: {
        total: {
          type: 'integer',
          description: '所选标签满足其一去重后的观众数',
        },
      },
    },
    ActivityAudienceImportByTagsData: {
      type: 'object',
      required: ['total', 'created', 'updated', 'skipped'],
      properties: {
        total: { type: 'integer', description: '命中去重观众数' },
        created: { type: 'integer', description: '新建行数' },
        updated: { type: 'integer', description: '补写导入人数' },
        skipped: { type: 'integer', description: '已有导入人跳过数' },
      },
    },

    MaterialAuditRecord: {
      type: 'object',
      properties: {
        id: { type: 'string', description: '审核记录 id 或合成 submit id' },
        action: {
          type: 'string',
          enum: ['submit', 'ai', 'risk'],
          description: 'submit 发起申请 / ai AI审核 / risk 风控审核',
        },
        result: {
          type: 'string',
          enum: ['passed', 'rejected'],
          description: 'passed 已通过 / rejected 已驳回；submit 不返回',
        },
        operatorId: { $ref: '#/components/schemas/EmployeeId' },
        operatorName: { type: 'string' },
        departmentName: { type: 'string', description: '无则省略' },
        createdTime: { type: 'string' },
      },
    },
    MaterialAuditListData: {
      type: 'object',
      properties: {
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/MaterialAuditRecord' },
        },
      },
    },
    PasscodeData: {
      type: 'object',
      required: ['passcode'],
      properties: {
        passcode: { type: 'string', pattern: "^\\d{9}$", description: '9 位数字开播口令' },
      },
    },
    PasscodeHostEnterRequest: {
      type: 'object',
      required: ['passcode'],
      properties: {
        passcode: { type: 'string', pattern: "^\\d{9}$" },
        nickname: { type: 'string', maxLength: 30, description: '讲师名称；默认「讲师」' },
      },
    },
    PasscodeHostEnterData: {
      type: 'object',
      properties: {
        activityId: { $ref: '#/components/schemas/BusinessUuid' },
        provider: { $ref: '#/components/schemas/Provider' },
        hostUrl: { type: 'string', format: 'uri' },
        role: { type: 'integer', enum: [1], description: '1 主播' },
        nickname: { type: 'string' },
      },
    },
    ActivityAudienceListItem: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64', description: '名单行 id' },
        viewerId: { type: 'integer', format: 'int64' },
        nickname: { type: 'string' },
        avatarUrl: { type: 'string' },
        entitlementStatus: {
          type: 'integer',
          enum: [0, 1],
          description: '0 无权益 / 1 有权益',
        },
        inviterDisplayName: { type: 'string' },
        inviterKind: {
          type: 'string',
          enum: ['employee'],
          description:
            'employee 员工；有所属邀请人时固定此值（本站不落观众邀请，故列表不会出现 audience）',
        },
        inviteTime: { type: 'string' },
        tagIds: { type: 'array', items: { type: 'integer', format: 'int64' } },
        tagLabels: { type: 'array', items: { type: 'string' } },
        totalWatchDurationSec: { type: 'integer' },
        liveWatchDurationSec: { type: 'integer' },
        replayWatchDurationSec: { type: 'integer' },
        commentCount: { type: 'integer' },
        dealAmount: { type: 'string', example: '0.00' },
      },
    },
    ActivityAudienceListData: {
      type: 'object',
      properties: {
        page: { type: 'integer' },
        pageSize: { type: 'integer' },
        total: { type: 'integer', format: 'int64' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/ActivityAudienceListItem' },
        },
      },
    },
    ActivityAudienceCreateRequest: {
      type: 'object',
      properties: {
        phone: { type: 'string', description: '与 viewerId 二选一' },
        viewerId: { type: 'integer', format: 'int64', description: '与 phone 二选一' },
      },
    },
    ActivityAudienceCreateData: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        viewerId: { type: 'integer', format: 'int64' },
        created: { type: 'boolean' },
      },
    },
    ActivityAudienceImportData: {
      type: 'object',
      properties: {
        importId: { type: 'integer', format: 'int64' },
        total: { type: 'integer' },
        created: { type: 'integer' },
        updated: { type: 'integer' },
        skipped: { type: 'integer' },
        failed: { type: 'integer' },
        errors: {
          type: 'array',
          maxItems: 50,
          items: {
            type: 'object',
            properties: {
              row: { type: 'integer' },
              phone: { type: 'string' },
              reason: { type: 'string' },
            },
          },
        },
      },
    },
    ActivityAudienceImportHistoryItem: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        importTime: { type: 'string' },
        importerId: { $ref: '#/components/schemas/EmployeeId' },
        importerName: { type: 'string' },
        successCount: { type: 'integer' },
        failCount: { type: 'integer' },
      },
    },
    ActivityAudienceImportHistoryData: {
      type: 'object',
      properties: {
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/ActivityAudienceImportHistoryItem' },
        },
      },
    },
    ActivityAudienceEntitlementRequest: {
      type: 'object',
      required: ['entitlementStatus'],
      properties: {
        entitlementStatus: {
          type: 'integer',
          enum: [0, 1],
          description: '0 无权益 / 1 有权益',
        },
      },
    },
    ActivityAudienceEntitlementData: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        entitlementStatus: {
          type: 'integer',
          enum: [0, 1],
          description: '0 无权益 / 1 有权益',
        },
      },
    },
    ActivityAudienceCommentItem: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        viewerId: { type: 'integer', format: 'int64' },
        guestId: { type: 'string' },
        nickname: { type: 'string' },
        content: { type: 'string' },
        commentType: {
          type: 'integer',
          enum: [1, 2],
          description: '1 聊天 / 2 提问',
        },
        commentTime: { type: 'string' },
        deleted: { type: 'boolean' },
      },
    },
    ActivityAudienceCommentListData: {
      type: 'object',
      properties: {
        total: { type: 'integer', format: 'int64' },
        page: { type: 'integer' },
        pageSize: { type: 'integer' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/ActivityAudienceCommentItem' },
        },
      },
    },
    ViewerListItem: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        nickname: { type: 'string' },
        avatarUrl: { type: 'string' },
        registerChannel: { type: 'integer', enum: [1, 2], description: '1 微信 / 2 导入' },
        phoneMasked: { type: 'string' },
        status: { type: 'integer', enum: [0, 1], description: '0 禁用 / 1 正常' },
        banComment: { type: 'integer', enum: [0, 1], description: '0 关闭 / 1 开启' },
        banPurchase: { type: 'integer', enum: [0, 1], description: '0 关闭 / 1 开启' },
        banAccess: { type: 'integer', enum: [0, 1], description: '0 关闭 / 1 开启' },
        createdTime: { type: 'string' },
        lastVisitTime: { type: 'string' },
        consumeCount: { type: 'integer' },
        consumeAmount: { type: 'string' },
        totalLearnDurationSec: { type: 'integer' },
        todayLearnDurationSec: { type: 'integer' },
        completedCourseCount: { type: 'integer' },
        tagIds: { type: 'array', items: { type: 'integer', format: 'int64' } },
        tagLabels: { type: 'array', items: { type: 'string' } },
      },
    },
    ViewerListData: {
      type: 'object',
      properties: {
        page: { type: 'integer' },
        pageSize: { type: 'integer' },
        total: { type: 'integer', format: 'int64' },
        list: { type: 'array', items: { $ref: '#/components/schemas/ViewerListItem' } },
      },
    },
    ViewerBlacklistUpdateRequest: {
      type: 'object',
      required: ['banComment', 'banPurchase', 'banAccess'],
      properties: {
        banComment: { type: 'integer', enum: [0, 1], description: '0 关闭 / 1 开启' },
        banPurchase: { type: 'integer', enum: [0, 1], description: '0 关闭 / 1 开启' },
        banAccess: { type: 'integer', enum: [0, 1], description: '0 关闭 / 1 开启' },
      },
    },
    ViewerBlacklistUpdateData: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        banComment: { type: 'integer', enum: [0, 1], description: '0 关闭 / 1 开启' },
        banPurchase: { type: 'integer', enum: [0, 1], description: '0 关闭 / 1 开启' },
        banAccess: { type: 'integer', enum: [0, 1], description: '0 关闭 / 1 开启' },
        status: { type: 'integer', enum: [0, 1], description: '0 禁用 / 1 正常' },
      },
    },
    ViewerTagsUpdateRequest: {
      type: 'object',
      required: ['tagIds'],
      properties: {
        tagIds: { type: 'array', items: { type: 'integer', format: 'int64' } },
      },
    },
    ViewerTagsUpdateData: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        tagIds: { type: 'array', items: { type: 'integer', format: 'int64' } },
        tagLabels: { type: 'array', items: { type: 'string' } },
      },
    },
    ViewerLearnStatsSummaryItem: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        nickname: { type: 'string' },
        avatarUrl: { type: 'string' },
        tagIds: { type: 'array', items: { type: 'integer', format: 'int64' } },
        tagLabels: { type: 'array', items: { type: 'string' } },
        totalLearnDurationSec: { type: 'integer' },
        totalLearnDays: { type: 'integer' },
        viewedCourseCount: { type: 'integer' },
        completedCourseCount: { type: 'integer' },
      },
    },
    ViewerLearnStatsSummaryData: {
      type: 'object',
      properties: {
        page: { type: 'integer' },
        pageSize: { type: 'integer' },
        total: { type: 'integer', format: 'int64' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/ViewerLearnStatsSummaryItem' },
        },
      },
    },
    ViewerLearnStatsCourseItem: {
      type: 'object',
      required: [
        'id',
        'viewerId',
        'nickname',
        'tagIds',
        'tagLabels',
        'lessonNodeId',
        'totalLearnDurationSec',
        'progressPercent',
        'lessonDurationSec',
      ],
      properties: {
        id: { type: 'integer', format: 'int64', description: 'course_lesson_progress.id' },
        viewerId: { type: 'integer', format: 'int64', description: 'viewer.id' },
        nickname: { type: 'string', description: '观众展示昵称' },
        avatarUrl: { type: 'string', description: '头像 URL；无则省略' },
        tagIds: { type: 'array', items: { type: 'integer', format: 'int64' }, description: '标签 id 列表；无则 []' },
        tagLabels: { type: 'array', items: { type: 'string' }, description: '标签名列表；无则 []' },
        courseId: { type: 'string', description: '本站课程 UUID' },
        lessonNodeId: { type: 'integer', format: 'int64', description: 'course_catalog_node.id（小节）' },
        lessonName: { type: 'string', description: '小节标题；节点缺失时可省略' },
        totalLearnDurationSec: { type: 'integer', description: '该小节累计学习秒数' },
        progressPercent: { type: 'integer', description: '学习进度 0～100' },
        firstLearnTime: { type: 'string', description: '首次参与时间 yyyy-MM-dd HH:mm:ss；可省略' },
        completedTime: { type: 'string', description: '完成时间 yyyy-MM-dd HH:mm:ss；未完成省略' },
        lessonType: {
          type: 'string',
          description: '小节内容类型中文：直播 / 图片 / 视频 / 文档 / 文章；未知可省略',
        },
        lessonDurationSec: {
          type: 'integer',
          description: '课程资源时长（秒）。视频：解析素材 meta（mm:ss 或 h:mm:ss）；解不出或非视频为 0',
        },
      },
    },
    ViewerLearnStatsCourseListData: {
      type: 'object',
      required: ['page', 'pageSize', 'total', 'list'],
      properties: {
        page: { type: 'integer' },
        pageSize: { type: 'integer' },
        total: { type: 'integer', format: 'int64' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/ViewerLearnStatsCourseItem' },
          description: '小节学习行；无进度时为空数组',
        },
      },
    },
    ViewerTagGroupBrief: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        name: { type: 'string' },
        isDefault: { type: 'boolean' },
      },
    },
    ViewerTagGroupPageItem: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        name: { type: 'string' },
        isDefault: { type: 'boolean' },
        tagCount: { type: 'integer' },
      },
    },
    ViewerTagGroupPageData: {
      type: 'object',
      properties: {
        page: { type: 'integer' },
        pageSize: { type: 'integer' },
        total: { type: 'integer', format: 'int64' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/ViewerTagGroupPageItem' },
        },
      },
    },
    ViewerTagGroupCreateRequest: {
      type: 'object',
      required: ['name'],
      properties: { name: { type: 'string', maxLength: 10 } },
    },
    ViewerTagGroupUpdateRequest: {
      type: 'object',
      required: ['name'],
      properties: { name: { type: 'string', maxLength: 10 } },
    },
    ViewerTagListItem: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        name: { type: 'string' },
        type: { type: 'integer', enum: [1, 2], description: '1 手动 / 2 自动' },
        definitionSummary: { type: 'string' },
        userCount: { type: 'integer' },
        groupId: { type: 'integer', format: 'int64' },
        groupName: { type: 'string' },
        createdTime: { type: 'string' },
      },
    },
    ViewerTagListData: {
      type: 'object',
      properties: {
        page: { type: 'integer' },
        pageSize: { type: 'integer' },
        total: { type: 'integer', format: 'int64' },
        list: { type: 'array', items: { $ref: '#/components/schemas/ViewerTagListItem' } },
      },
    },
    ViewerTagCreateRequest: {
      type: 'object',
      required: ['name', 'type'],
      properties: {
        name: { type: 'string', maxLength: 20 },
        type: { type: 'integer', enum: [1], description: '1 手动' },
        groupId: { type: 'integer', format: 'int64' },
      },
    },
    ViewerTagUpdateRequest: {
      type: 'object',
      required: ['name'],
      properties: {
        name: { type: 'string', maxLength: 20 },
        groupId: { type: 'integer', format: 'int64' },
      },
    },
    ChatFilterAdminData: {
      type: 'object',
      required: ['pageUrl'],
      properties: { pageUrl: { type: 'string', format: 'uri' } },
    },

    ActivityUserDataSummary: {
      type: 'object',
      required: ['visitorCount', 'viewCount', 'avgStayDurationSec'],
      properties: {
        visitorCount: {
          type: 'integer',
          description: '访客数；等于 rows 中 type=live 的观看人数',
        },
        viewCount: {
          type: 'integer',
          description: '浏览量；等于 rows 中 type=live 的观看人次',
        },
        avgStayDurationSec: {
          type: 'integer',
          description: '人均停留时长（整秒）；等于 live 行人均观看时长',
        },
      },
    },
    ActivityUserDataRow: {
      type: 'object',
      required: ['type', 'viewerCount', 'viewCount', 'avgWatchDurationSec'],
      properties: {
        type: {
          type: 'string',
          enum: ['live_replay', 'live', 'replay'],
          description: 'live_replay 直播+回放 / live 直播 / replay 回放',
        },
        viewerCount: { type: 'integer', description: '观看人数' },
        viewCount: { type: 'integer', description: '观看人次' },
        avgWatchDurationSec: {
          type: 'integer',
          description: '人均观看时长（整秒）',
        },
      },
    },
    ActivityUserDataTrendDefault: {
      type: 'object',
      required: ['startTime', 'endTime'],
      properties: {
        startTime: {
          type: 'string',
          description: '趋势图默认起点，yyyy-MM-dd HH:mm:ss',
        },
        endTime: {
          type: 'string',
          description: '趋势图默认终点，yyyy-MM-dd HH:mm:ss',
        },
      },
    },
    ActivityUserData: {
      type: 'object',
      required: ['id', 'summary', 'rows', 'trendDefault'],
      properties: {
        id: {
          $ref: '#/components/schemas/BusinessUuid',
          description: '本站活动 UUID',
        },
        summary: {
          $ref: '#/components/schemas/ActivityUserDataSummary',
          description: '顶部汇总；恒等于 rows 中 type=live',
        },
        rows: {
          type: 'array',
          minItems: 3,
          maxItems: 3,
          description: '固定三行，顺序 live_replay → live → replay',
          items: { $ref: '#/components/schemas/ActivityUserDataRow' },
        },
        trendDefault: {
          $ref: '#/components/schemas/ActivityUserDataTrendDefault',
          description: '在线趋势图默认起止时间',
        },
      },
    },

    CatalogNodeType: {
      type: 'integer',
      enum: [1, 2, 3],
      description: '1 章节 / 2 子课程 / 3 小节；库：INT',
    },
    CatalogContentKind: {
      type: 'integer',
      enum: [1, 2, 3, 4, 5],
      description: '1 直播 / 2 图片 / 3 视频 / 4 文档 / 5 文章；库：INT',
    },
    CatalogReorderMode: {
      type: 'string',
      enum: ['chapters', 'lessons'],
      description: 'chapters 章排序 / lessons 小节排序',
    },
    CourseCatalogNode: {
      type: 'object',
      required: ['id', 'courseId', 'parentId', 'nodeType', 'title', 'sortOrder'],
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: '目录节点 id；库：BIGINT',
        },
        courseId: {
          $ref: '#/components/schemas/BusinessUuid',
          description: '所属课程 UUID',
        },
        parentId: {
          type: 'integer',
          format: 'int64',
          description: '父节点 id；根为 0；库：BIGINT',
        },
        nodeType: { $ref: '#/components/schemas/CatalogNodeType' },
        title: {
          type: 'string',
          maxLength: 30,
          description: '节点标题；库：VARCHAR(30)',
        },
        coverMaterialId: {
          $ref: '#/components/schemas/BusinessUuid',
          description: '子课程封面素材 UUID；无则省略',
        },
        coverUrl: { type: 'string', description: '子课程封面 URL；无则省略' },
        contentKind: {
          $ref: '#/components/schemas/CatalogContentKind',
          description: '仅小节；内容类型',
        },
        contentId: {
          type: 'string',
          description: '小节内容 id（直播活动 UUID 或素材 UUID）；仅小节',
        },
        sortOrder: {
          type: 'integer',
          description: '同父排序；0 表示未自定义；库：INT',
        },
        chapterCount: {
          type: 'integer',
          description: '子课程下章节数；仅子课程返回',
        },
        lessonCount: {
          type: 'integer',
          description: '子课程下小节数；仅子课程返回',
        },
        createdTime: {
          type: 'string',
          description: '创建时间，yyyy-MM-dd HH:mm:ss',
        },
      },
    },
    CourseCatalogListData: {
      type: 'object',
      required: ['list'],
      properties: {
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/CourseCatalogNode' },
          description: '扁平目录节点列表（按展示序）',
        },
        contentCount: {
          type: 'integer',
          description: '课程内容数（小节数）；部分写接口回写',
        },
      },
    },
    CourseCatalogUsedContentsData: {
      type: 'object',
      required: ['lives', 'materials'],
      properties: {
        lives: {
          type: 'array',
          items: { $ref: '#/components/schemas/BusinessUuid' },
          description: '本课已占用的直播活动 UUID',
        },
        materials: {
          type: 'array',
          items: { $ref: '#/components/schemas/BusinessUuid' },
          description: '本课已占用的素材 UUID',
        },
      },
    },
    CourseCatalogBindLivesRequest: {
      type: 'object',
      required: ['parentId', 'activityIds'],
      properties: {
        parentId: {
          type: 'integer',
          format: 'int64',
          description: '父节点 id；有子课后不可为 0',
        },
        activityIds: {
          type: 'array',
          minItems: 1,
          items: { $ref: '#/components/schemas/BusinessUuid' },
          description: '要绑定的直播活动 UUID 列表',
        },
      },
    },
    CourseCatalogBindMaterialsRequest: {
      type: 'object',
      required: ['parentId', 'materialIds'],
      properties: {
        parentId: {
          type: 'integer',
          format: 'int64',
          description: '父节点 id；有子课后不可为 0',
        },
        materialIds: {
          type: 'array',
          minItems: 1,
          items: { $ref: '#/components/schemas/BusinessUuid' },
          description: '要绑定的素材 UUID 列表',
        },
      },
    },
    CourseCatalogChapterCreateRequest: {
      type: 'object',
      required: ['parentId', 'title'],
      properties: {
        parentId: {
          type: 'integer',
          format: 'int64',
          description: '父节点 id；有子课后须为子课程 id',
        },
        title: {
          type: 'string',
          minLength: 1,
          maxLength: 30,
          description: '章节标题，1～30 字',
        },
      },
    },
    CourseCatalogChapterUpdateRequest: {
      type: 'object',
      required: ['id', 'title'],
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: '本课章节节点 id',
        },
        title: {
          type: 'string',
          minLength: 1,
          maxLength: 30,
          description: '章节标题，1～30 字',
        },
      },
    },
    CourseCatalogLessonUpdateRequest: {
      type: 'object',
      required: ['id', 'title'],
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: '本课小节节点 id',
        },
        title: {
          type: 'string',
          minLength: 1,
          maxLength: 30,
          description: '小节标题，1～30 字',
        },
      },
    },
    CourseCatalogChapterDeleteRequest: {
      type: 'object',
      required: ['id', 'keepContents'],
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: '要软删的章节节点 id',
        },
        keepContents: {
          type: 'boolean',
          description:
            'true 保留其下小节（可迁到 targetChapterId）；false 级联软删小节并重算 contentCount',
        },
        targetChapterId: {
          type: 'integer',
          format: 'int64',
          description:
            '可选；keepContents=true 时把直属小节迁到该章节后再软删本节点',
        },
      },
    },
    CourseCatalogSubcourseCreateRequest: {
      type: 'object',
      required: ['title', 'coverMaterialId'],
      properties: {
        title: {
          type: 'string',
          minLength: 1,
          maxLength: 30,
          description: '子课程名称，1～30 字',
        },
        coverMaterialId: {
          $ref: '#/components/schemas/BusinessUuid',
          description: '封面素材 UUID',
        },
        moveRootContents: {
          type: 'boolean',
          description:
            '首子课且根下已有章节/小节时须 true，把根内容迁入新子课程',
        },
      },
    },
    CourseCatalogSubcourseUpdateRequest: {
      type: 'object',
      required: ['id', 'title', 'coverMaterialId'],
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: '本课子课程节点 id',
        },
        title: {
          type: 'string',
          minLength: 1,
          maxLength: 30,
          description: '子课程名称，1～30 字',
        },
        coverMaterialId: {
          $ref: '#/components/schemas/BusinessUuid',
          description: '封面素材 UUID',
        },
      },
    },
    CourseCatalogSubcourseDeleteRequest: {
      type: 'object',
      required: ['id', 'keepContents'],
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: '要软删的子课程节点 id',
        },
        keepContents: {
          type: 'boolean',
          description:
            'true 保留其下章节/小节（可迁到 targetSubcourseId）；false 级联软删并重算 contentCount',
        },
        targetSubcourseId: {
          type: 'integer',
          format: 'int64',
          description:
            '可选；keepContents=true 时把直属章节与根小节迁到该子课程后再软删本节点',
        },
      },
    },
    CourseCatalogSubcourseReorderRequest: {
      type: 'object',
      required: ['ids'],
      properties: {
        ids: {
          type: 'array',
          minItems: 1,
          items: { type: 'integer', format: 'int64' },
          description: '本课全部未软删子课程 id，按目标顺序',
        },
      },
    },
    CourseCatalogReorderItem: {
      type: 'object',
      required: ['id', 'parentId'],
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: '节点 id',
        },
        parentId: {
          type: 'integer',
          format: 'int64',
          description: '目标父节点 id（小节可改挂；章节不可变）',
        },
      },
    },
    CourseCatalogReorderRequest: {
      type: 'object',
      required: ['parentId', 'mode', 'items'],
      properties: {
        parentId: {
          type: 'integer',
          format: 'int64',
          description: '目录上下文：根 0 或子课程 id',
        },
        mode: { $ref: '#/components/schemas/CatalogReorderMode' },
        items: {
          type: 'array',
          minItems: 1,
          items: { $ref: '#/components/schemas/CourseCatalogReorderItem' },
          description: '按 mode 覆盖该上下文下须排序的节点',
        },
      },
    },
    CourseCatalogDeleteData: {
      type: 'object',
      required: ['id'],
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: '已软删节点 id',
        },
      },
    },

  }

  // response wrappers
  for (const [name, data] of Object.entries({
    ApiResponseId: { $ref: '#/components/schemas/IdData' },
    ApiResponseCaptcha: { $ref: '#/components/schemas/CaptchaData' },
    ApiResponseLoginPending: { $ref: '#/components/schemas/LoginPendingData' },
    ApiResponseLoginQuery: { $ref: '#/components/schemas/LoginQueryData' },
    ApiResponseTokenPair: { $ref: '#/components/schemas/TokenPairData' },
    ApiResponseMe: { $ref: '#/components/schemas/EmployeeBrief' },
    ApiResponseUploadImage: { $ref: '#/components/schemas/UploadImageData' },
    ApiResponseTosSts: { $ref: '#/components/schemas/TosStsData' },
    ApiResponseMaterialGroups: {
      type: 'array',
      items: { $ref: '#/components/schemas/MaterialGroup' },
    },
    ApiResponseMaterialGroup: { $ref: '#/components/schemas/MaterialGroup' },
    ApiResponseMaterialList: { $ref: '#/components/schemas/MaterialListData' },
    ApiResponseMaterialItem: { $ref: '#/components/schemas/MaterialItem' },
    ApiResponseMaterialRegister: {
      $ref: '#/components/schemas/MaterialRegisterData',
    },
    ApiResponseArticleDetail: { $ref: '#/components/schemas/ArticleDetail' },
    ApiResponseActivityList: { $ref: '#/components/schemas/ActivityListData' },
    ApiResponseActivityDetail: { $ref: '#/components/schemas/ActivityDetail' },
    ApiResponseRecordList: {
      $ref: '#/components/schemas/ActivityRecordListData',
    },
    ApiResponseRecordDownload: {
      $ref: '#/components/schemas/ActivityRecordDownloadData',
    },
    ApiResponseEndActivity: { $ref: '#/components/schemas/EndActivityData' },
    ApiResponseLiveStart: { $ref: '#/components/schemas/EndActivityData' },
    ApiResponseMembers: { $ref: '#/components/schemas/ActivityMemberListData' },
    ApiResponseAcceptMember: {
      $ref: '#/components/schemas/AcceptActivityMemberData',
    },
    ApiResponseHost: { $ref: '#/components/schemas/HostData' },
    ApiResponsePushStream: { $ref: '#/components/schemas/PushStreamData' },
    ApiResponseStreamStatus: { $ref: '#/components/schemas/StreamStatusData' },
    ApiResponseStats: { $ref: '#/components/schemas/ActivityStats' },
    ApiResponseOnlineTrend: { $ref: '#/components/schemas/OnlineTrend' },
    ApiResponsePresenceTrend: { $ref: '#/components/schemas/PresenceTrend' },
    ApiResponseAttendeeList: { $ref: '#/components/schemas/AttendeeListData' },
    ApiResponseSales: { $ref: '#/components/schemas/SalesSettingsData' },
    ApiResponseShelf: { $ref: '#/components/schemas/ShelfData' },
    ApiResponseAuditSubmit: { $ref: '#/components/schemas/AuditSubmitData' },
    ApiResponseAudits: {
      type: 'object',
      properties: {
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/AuditRecord' },
        },
      },
    },
    ApiResponseEmployees: {
      type: 'object',
      properties: {
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/EmployeeBrief' },
        },
      },
    },
    ApiResponseOrgDeptTree: {
      type: 'array',
      items: { $ref: '#/components/schemas/OrgDeptNode' },
    },
    ApiResponseOrgEmployees: {
      $ref: '#/components/schemas/OrgEmployeeListData',
    },
    ApiResponseLiveEnabled: { $ref: '#/components/schemas/LiveEnabledData' },
    ApiResponseOrgRoles: {
      type: 'array',
      items: { $ref: '#/components/schemas/OrgRoleBrief' },
    },
    ApiResponseOrgRoleDetail: { $ref: '#/components/schemas/OrgRoleDetail' },
    ApiResponseLiveReportResult: {
      $ref: '#/components/schemas/LiveReportResult',
    },
    ApiResponseLiveReportDetail: {
      $ref: '#/components/schemas/LiveReportDetail',
    },
    ApiResponseLiveReportList: {
      $ref: '#/components/schemas/LiveReportListData',
    },
    ApiResponseLiveReportAudits: {
      $ref: '#/components/schemas/LiveReportAuditList',
    },
    ApiResponseCourseList: { $ref: '#/components/schemas/CourseListData' },
    ApiResponseCourseDetail: { $ref: '#/components/schemas/CourseDetail' },
    ApiResponseCourseSales: { $ref: '#/components/schemas/CourseSalesUpdateData' },
    ApiResponseOutsideCourseList: {
      $ref: '#/components/schemas/OutsideCourseListData',
    },
    ApiResponseCourseUserList: { $ref: '#/components/schemas/CourseUserListData' },
    ApiResponseCourseUserEntitlement: {
      $ref: '#/components/schemas/CourseUserEntitlementData',
    },
    ApiResponseCourseUserExpire: {
      $ref: '#/components/schemas/CourseUserExpireData',
    },
    ApiResponseCourseUserCreate: {
      $ref: '#/components/schemas/CourseUserCreateData',
    },
    ApiResponseCourseUserImport: {
      $ref: '#/components/schemas/CourseUserImportData',
    },
    ApiResponseCourseUserImportByTagsEstimate: {
      $ref: '#/components/schemas/CourseUserImportByTagsEstimateData',
    },
    ApiResponseCourseUserImportByTags: {
      $ref: '#/components/schemas/CourseUserImportByTagsData',
    },
    ApiResponseCourseUserImportHistory: {
      $ref: '#/components/schemas/CourseUserImportHistoryData',
    },
    ApiResponseActivityAudienceImportByTagsEstimate: {
      $ref: '#/components/schemas/ActivityAudienceImportByTagsEstimateData',
    },
    ApiResponseActivityAudienceImportByTags: {
      $ref: '#/components/schemas/ActivityAudienceImportByTagsData',
    },

    ApiResponseMaterialAudits: { $ref: '#/components/schemas/MaterialAuditListData' },
    ApiResponsePasscode: { $ref: '#/components/schemas/PasscodeData' },
    ApiResponsePasscodeHostEnter: { $ref: '#/components/schemas/PasscodeHostEnterData' },
    ApiResponseActivityAudienceList: { $ref: '#/components/schemas/ActivityAudienceListData' },
    ApiResponseActivityAudienceCreate: { $ref: '#/components/schemas/ActivityAudienceCreateData' },
    ApiResponseActivityAudienceImport: { $ref: '#/components/schemas/ActivityAudienceImportData' },
    ApiResponseActivityAudienceImportHistory: {
      $ref: '#/components/schemas/ActivityAudienceImportHistoryData',
    },
    ApiResponseActivityAudienceEntitlement: {
      $ref: '#/components/schemas/ActivityAudienceEntitlementData',
    },
    ApiResponseActivityAudienceComments: {
      $ref: '#/components/schemas/ActivityAudienceCommentListData',
    },
    ApiResponseChatFilterAdmin: { $ref: '#/components/schemas/ChatFilterAdminData' },
    ApiResponseViewerList: { $ref: '#/components/schemas/ViewerListData' },
    ApiResponseViewerBlacklist: { $ref: '#/components/schemas/ViewerBlacklistUpdateData' },
    ApiResponseViewerTags: { $ref: '#/components/schemas/ViewerTagsUpdateData' },
    ApiResponseViewerLearnSummary: { $ref: '#/components/schemas/ViewerLearnStatsSummaryData' },
    ApiResponseViewerLearnCourses: { $ref: '#/components/schemas/ViewerLearnStatsCourseListData' },
    ApiResponseViewerTagGroups: {
      type: 'array',
      items: { $ref: '#/components/schemas/ViewerTagGroupBrief' },
    },
    ApiResponseViewerTagGroupPage: { $ref: '#/components/schemas/ViewerTagGroupPageData' },
    ApiResponseViewerTagGroupItem: { $ref: '#/components/schemas/ViewerTagGroupPageItem' },
    ApiResponseViewerTagList: { $ref: '#/components/schemas/ViewerTagListData' },
    ApiResponseViewerTagDetail: { $ref: '#/components/schemas/ViewerTagListItem' },
    ApiResponseActivityUserData: { $ref: '#/components/schemas/ActivityUserData' },
    ApiResponseCourseCatalogList: {
      $ref: '#/components/schemas/CourseCatalogListData',
    },
    ApiResponseCourseCatalogNode: {
      $ref: '#/components/schemas/CourseCatalogNode',
    },
    ApiResponseCourseCatalogUsedContents: {
      $ref: '#/components/schemas/CourseCatalogUsedContentsData',
    },
    ApiResponseCourseCatalogDelete: {
      $ref: '#/components/schemas/CourseCatalogDeleteData',
    },
    /** 数字主键成功回包：data.id 为 int64（如软删标签/标签分组） */
    ApiResponseIntId: { $ref: '#/components/schemas/IdDataInt' },

  })) {
    schemas[name] = wrapData(data)
  }

  const activityId = { $ref: '#/components/parameters/ActivityId' }
  const materialId = { $ref: '#/components/parameters/MaterialId' }
  /** path 占位 `{id}`（如 /courses/detail/{id}） */
  const courseId = { $ref: '#/components/parameters/CourseId' }
  /** path 占位 `{courseId}`（目录 / 学员等）——须与 URL 占位同名，否则 Apifox 会叠出 id+courseId */
  const courseIdPath = { $ref: '#/components/parameters/CourseIdPath' }
  const liveReportId = { $ref: '#/components/parameters/LiveReportId' }

  const paths = {
    '/api/v1/auth/captcha': {
      get: {
        tags: ['登录鉴权'],
        summary: '图形验证码',
        description: '无鉴权。imageBase64 为 PNG，固定 120×48 像素。',
        operationId: 'getCaptcha',
        security: [],
        responses: ok200('#/components/schemas/ApiResponseCaptcha'),
      },
    },
    '/api/v1/auth/login': {
      post: {
        tags: ['登录鉴权'],
        summary: '账号密码登录（返回微信 state，不发 JWT）',
        operationId: 'login',
        security: [],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/LoginRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseLoginPending'),
      },
    },
    '/api/v1/auth/wechat/callback': {
      get: {
        tags: ['登录鉴权'],
        summary: '微信扫码回调（GET）',
        description: '微信 redirect；建议返回极简 HTML。正式 Token 只经 login/query。',
        operationId: 'wechatCallbackGet',
        security: [],
        parameters: [
          { name: 'code', in: 'query', schema: { type: 'string' } },
          { name: 'state', in: 'query', schema: { type: 'string' } },
        ],
        responses: {
          '200': { description: 'HTML 或业务结果' },
        },
      },
      post: {
        tags: ['登录鉴权'],
        summary: '微信扫码回调（POST）',
        operationId: 'wechatCallbackPost',
        security: [],
        parameters: [
          { name: 'code', in: 'query', schema: { type: 'string' } },
          { name: 'state', in: 'query', schema: { type: 'string' } },
        ],
        responses: {
          '200': { description: 'HTML 或业务结果' },
        },
      },
    },
    '/api/v1/auth/login/query': {
      get: {
        tags: ['登录鉴权'],
        summary: '轮询扫码结果 / 取 JWT',
        operationId: 'loginQuery',
        security: [],
        parameters: [
          {
            name: 'state',
            in: 'query',
            required: true,
            schema: { type: 'string' },
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseLoginQuery'),
      },
    },
    '/api/v1/auth/refresh': {
      post: {
        tags: ['登录鉴权'],
        summary: '刷新 access + refresh',
        operationId: 'refreshToken',
        security: [],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/RefreshRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseTokenPair'),
      },
    },
    '/api/v1/auth/me': {
      get: {
        tags: ['登录鉴权'],
        summary: '当前员工',
        operationId: 'getMe',
        security: bearer,
        responses: ok200('#/components/schemas/ApiResponseMe'),
      },
    },
    '/api/v1/uploads/image': {
      post: {
        tags: ['图片上传'],
        summary: '图片上传（代理微吼）',
        deprecated: true,
        description: 'D60 已废弃：请改用 TOS 直传（uploads/tos-sts + 登记）。',
        operationId: 'uploadImage',
        security: bearer,
        requestBody: {
          required: true,
          content: {
            'multipart/form-data': {
              schema: {
                type: 'object',
                required: ['file'],
                properties: {
                  file: {
                    type: 'string',
                    format: 'binary',
                    description: 'bmp/gif/jpg/jpeg/png/psd，≤5MB',
                  },
                },
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseUploadImage'),
      },
    },
    '/api/v1/uploads/tos-sts': {
      get: {
        tags: ['图片上传'],
        summary: '火山 TOS 临时上传凭证',
        operationId: 'getTosSts',
        security: bearer,
        responses: ok200('#/components/schemas/ApiResponseTosSts'),
      },
    },
    '/api/v1/materials/groups/list': {
      get: {
        tags: ['素材中心'],
        summary: '分组列表',
        operationId: 'listMaterialGroups',
        security: bearer,
        parameters: [
          {
            name: 'type',
            in: 'query',
            required: true,
            schema: { $ref: '#/components/schemas/MaterialType' },
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseMaterialGroups'),
      },
    },
    '/api/v1/materials/groups/create': {
      post: {
        tags: ['素材中心'],
        summary: '新建分组',
        operationId: 'createMaterialGroup',
        security: bearer,
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                required: ['type', 'name'],
                properties: {
                  type: { $ref: '#/components/schemas/MaterialType' },
                  name: { type: 'string', maxLength: 64 },
                },
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseMaterialGroup'),
      },
    },
    '/api/v1/materials/groups/rename/{id}': {
      put: {
        tags: ['素材中心'],
        summary: '重命名分组',
        operationId: 'renameMaterialGroup',
        security: bearer,
        parameters: [materialId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                required: ['name'],
                properties: { name: { type: 'string', maxLength: 64 } },
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseMaterialGroup'),
      },
    },
    '/api/v1/materials/groups/delete/{id}': {
      delete: {
        tags: ['素材中心'],
        summary: '删除分组',
        operationId: 'deleteMaterialGroup',
        security: bearer,
        parameters: [materialId],
        responses: ok200('#/components/schemas/ApiResponseId'),
      },
    },
    '/api/v1/materials/list': {
      get: {
        tags: ['素材中心'],
        summary: '素材分页列表',
        operationId: 'listMaterials',
        security: bearer,
        parameters: [
          {
            name: 'type',
            in: 'query',
            required: true,
            schema: { $ref: '#/components/schemas/MaterialType' },
          },
          {
            name: 'groupId',
            in: 'query',
            schema: { $ref: '#/components/schemas/BusinessUuid' },
          },
          { name: 'keyword', in: 'query', schema: { type: 'string' } },
          {
            name: 'auditStatus',
            in: 'query',
            schema: { $ref: '#/components/schemas/MaterialAuditStatus' },
            description:
              '联合审核：任一方驳回算驳回，否则任一方待审核算待审核，两方都通过才算通过。不传不过滤',
          },
          {
            name: 'selectableOnly',
            in: 'query',
            schema: { type: 'boolean', default: false },
            description: 'true 时仅两审均为 1（已通过）',
          },
          ...pageParams,
        ],
        responses: ok200('#/components/schemas/ApiResponseMaterialList'),
      },
    },
    '/api/v1/materials/create': {
      post: {
        tags: ['素材中心'],
        summary: '批量登记（TOS 上传成功后）',
        operationId: 'registerMaterials',
        security: bearer,
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/MaterialRegisterRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseMaterialRegister'),
      },
    },
    '/api/v1/materials/articles/create': {
      post: {
        tags: ['素材中心'],
        summary: '新建文章',
        operationId: 'createArticle',
        security: bearer,
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/ArticleCreateRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseMaterialItem'),
      },
    },
    '/api/v1/materials/articles/detail/{id}': {
      get: {
        tags: ['素材中心'],
        summary: '文章详情',
        operationId: 'getArticle',
        security: bearer,
        parameters: [materialId],
        responses: ok200('#/components/schemas/ApiResponseArticleDetail'),
      },
    },
    '/api/v1/materials/articles/update/{id}': {
      put: {
        tags: ['素材中心'],
        summary: '编辑文章',
        description: '待审核不可编辑；成功后两审回到 0',
        operationId: 'updateArticle',
        security: bearer,
        parameters: [materialId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/ArticleUpdateRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseMaterialItem'),
      },
    },
    '/api/v1/materials/rename/{id}': {
      put: {
        tags: ['素材中心'],
        summary: '修改展示名',
        operationId: 'renameMaterial',
        security: bearer,
        parameters: [materialId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                required: ['name'],
                properties: { name: { type: 'string', maxLength: 256 } },
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseMaterialItem'),
      },
    },
    '/api/v1/materials/delete/{id}': {
      delete: {
        tags: ['素材中心'],
        summary: '软删素材',
        operationId: 'deleteMaterial',
        security: bearer,
        parameters: [materialId],
        responses: ok200('#/components/schemas/ApiResponseId'),
      },
    },
    '/api/v1/materials/group/{id}': {
      put: {
        tags: ['素材中心'],
        summary: '移动分组',
        operationId: 'moveMaterialGroup',
        security: bearer,
        parameters: [materialId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                required: ['groupId'],
                properties: {
                  groupId: { $ref: '#/components/schemas/BusinessUuid' },
                },
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseMaterialItem'),
      },
    },
    '/api/v1/materials/image/{id}': {
      put: {
        tags: ['素材中心'],
        summary: '裁剪替换图片',
        operationId: 'replaceMaterialImage',
        security: bearer,
        parameters: [materialId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                required: ['url', 'objectKey'],
                properties: {
                  url: { type: 'string' },
                  objectKey: { type: 'string' },
                  fileSize: { type: 'integer' },
                  meta: { type: 'string' },
                },
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseMaterialItem'),
      },
    },
    '/api/v1/materials/ai-audit/{id}': {
      put: {
        tags: ['素材中心'],
        summary: '改 AI 审核状态',
        operationId: 'updateMaterialAiAudit',
        security: bearer,
        parameters: [materialId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                required: ['status'],
                properties: {
                  status: { $ref: '#/components/schemas/AuditWriteStatus' },
                },
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseMaterialItem'),
      },
    },
    '/api/v1/materials/risk-audit/{id}': {
      put: {
        tags: ['素材中心'],
        summary: '改风控合规状态',
        operationId: 'updateMaterialRiskAudit',
        security: bearer,
        parameters: [materialId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                required: ['status'],
                properties: {
                  status: { $ref: '#/components/schemas/AuditWriteStatus' },
                },
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseMaterialItem'),
      },
    },
    '/api/v1/activities/create': {
      post: {
        tags: ['活动管理'],
        summary: '创建活动',
        operationId: 'createActivity',
        security: bearer,
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/CreateActivityRequest' },
              examples: {
                video: {
                  summary: '视频直播',
                  value: {
                    provider: 'vhall',
                    title: '开发者大会',
                    startTime: '2026-10-01 20:00:00',
                    endTime: '2026-10-01 22:00:00',
                    type: 2,
                    introduction: '简介',
                    coverUrl: 'https://cnstatic01.e.vhall.com/upload/xxx.jpg',
                    ultraLowLatency: false,
                    listed: false,
                    hostIds: [10001],
                    ownerIds: [10001],
                    reportId: '11111111-1111-1111-1111-111111111111',
                    products: [],
                    coursewareMaterialIds: [],
                  },
                },
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseId'),
      },
    },
    '/api/v1/activities/list': {
      get: {
        tags: ['活动管理'],
        summary: '活动列表',
        operationId: 'listActivities',
        security: bearer,
        parameters: [
          ...pageParams,
          {
            name: 'keyword',
            in: 'query',
            schema: { type: 'string' },
            description: '标题或本站活动 UUID',
          },
          {
            name: 'state',
            in: 'query',
            schema: { $ref: '#/components/schemas/ActivityState' },
          },
          {
            name: 'provider',
            in: 'query',
            schema: { $ref: '#/components/schemas/Provider' },
          },
          {
            name: 'shelfStatus',
            in: 'query',
            schema: { $ref: '#/components/schemas/ShelfStatus' },
          },
          {
            name: 'startFrom',
            in: 'query',
            schema: { type: 'string', example: '2026-10-01' },
            description: 'yyyy-MM-dd',
          },
          {
            name: 'startTo',
            in: 'query',
            schema: { type: 'string', example: '2026-10-31' },
            description: 'yyyy-MM-dd',
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseActivityList'),
      },
    },
    '/api/v1/activities/detail/{id}': {
      get: {
        tags: ['活动管理'],
        summary: '活动详情',
        operationId: 'getActivity',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseActivityDetail'),
      },
    },
    '/api/v1/activities/update/{id}': {
      put: {
        tags: ['活动管理'],
        summary: '改活动（基本信息 Tab）',
        operationId: 'updateActivity',
        security: bearer,
        parameters: [activityId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/UpdateActivityRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseId'),
      },
    },
    '/api/v1/activities/delete/{id}': {
      delete: {
        tags: ['活动管理'],
        summary: '软删活动',
        operationId: 'deleteActivity',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseId'),
      },
    },
    '/api/v1/activities/playback/{id}': {
      put: {
        tags: ['活动管理'],
        summary: '回放设置',
        operationId: 'updatePlayback',
        security: bearer,
        parameters: [activityId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/PlaybackSettingsRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseId'),
      },
    },
    '/api/v1/activities/sales/{id}': {
      put: {
        tags: ['活动管理'],
        summary: '售卖设置',
        description: [
          '更新直播活动售卖设置，只写本站 activity 表，不改微吼 verify。',
          'sellType 仅允许 free（免费）/ encrypted（加密）/ specified（指定学员）；不接受 paid（付费已下线；列表/详情仍可读存量 paid；传 paid →「sellType 无效」）。',
          'sellAlone 必填。sellType=free 时服务端强制 accessValidityType=long；非 free 时必填 long / days（直播无 fixed）。',
          'days 时 accessValidityDays 必填且 ≥1，含义为直播实际结束后 N 天内可访问。',
          'encrypted 时 password 必填（恰好 6 位数字明文），服务端 AES 加密落库；非加密时清空密文。',
          '保存时清空 price / linePrice；并按有效期重算写入 activity.validity_label（免费或 long →「长期有效」；days →「{N}天有效」，如「7天有效」）。',
          '响应 data：id、sellType、validityLabel。活动不存在或已软删 → 400。',
        ].join(''),
        operationId: 'updateSales',
        security: bearer,
        parameters: [activityId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/SalesSettingsRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseSales'),
      },
    },
    '/api/v1/activities/end/{id}': {
      post: {
        tags: ['活动管理'],
        summary: '结束直播',
        operationId: 'endActivity',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseEndActivity'),
      },
    },
    '/api/v1/activities/shelf/{id}': {
      post: {
        tags: ['活动管理'],
        summary: '上架 / 下架',
        operationId: 'shelfActivity',
        security: bearer,
        parameters: [activityId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/ShelfRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseShelf'),
      },
    },
    '/api/v1/activities/audits/{id}': {
      get: {
        tags: ['活动管理'],
        summary: '审核记录',
        operationId: 'listActivityAudits',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseAudits'),
      },
    },
    '/api/v1/activities/audit/{id}': {
      post: {
        tags: ['活动管理'],
        summary: '提交审核',
        operationId: 'auditActivity',
        security: bearer,
        parameters: [activityId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/AuditSubmitRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseAuditSubmit'),
      },
    },
    '/api/v1/activities/records/{id}': {
      get: {
        tags: ['活动管理'],
        summary: '回放列表',
        description:
          '某场活动的回放文件列表。list[].id = 微吼 record_id，供下载接口与 create-demand 使用。非 vhall / 未接入 → 501。',
        operationId: 'listActivityRecords',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseRecordList'),
      },
    },
    '/api/v1/activities/records/{id}/download/{recordId}': {
      get: {
        tags: ['活动管理'],
        summary: '回放下载',
        description:
          '某场活动下一条回放的打包下载地址（管理后台回放「下载」）。路径 id=本站活动 UUID；recordId=回放列表 list[].id。按本库状态推进 TOS（D88）。非 vhall / 未接入 → 501。有 tosUrl 时直接下载。',
        operationId: 'downloadActivityRecord',
        security: bearer,
        parameters: [
          activityId,
          {
            name: 'recordId',
            in: 'path',
            required: true,
            schema: {
              type: 'integer',
              format: 'int64',
              example: 5814669,
            },
            description:
              '微吼回放 id（同 GET /api/v1/activities/records/{id} 的 list[].id）',
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseRecordDownload'),
      },
    },
    '/api/v1/activities/host/{id}': {
      get: {
        tags: ['活动管理'],
        summary: '角色参会链接',
        description:
          '主持人、嘉宾、助理都用本接口取本人的参会 iframe 地址（hostUrl）。供本站 /live/studio/{id}?role= 页 iframe src，不要 window.open。role 省略按主播。嘉宾/助理须已是本场对应角色，否则 400。canBroadcast 不为真 → 400。',
        operationId: 'getActivityHost',
        security: bearer,
        parameters: [
          activityId,
          {
            name: 'role',
            in: 'query',
            required: false,
            schema: {
              type: 'integer',
              enum: [1, 2, 3],
              default: 1,
              description: '1 主播 / 2 嘉宾 / 3 助理；省略按主播',
            },
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseHost'),
      },
    },
    '/api/v1/activities/members/list/{id}': {
      get: {
        tags: ['活动管理'],
        summary: '开播成员名单',
        description:
          '主播/助理/嘉宾（D57）。按 role 升序，同角色内按 sort_order。已软删 → 400。',
        operationId: 'listActivityMembers',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseMembers'),
      },
    },
    '/api/v1/activities/members/update/{id}': {
      put: {
        tags: ['活动管理'],
        summary: '更新开播成员资料',
        description:
          '只改一名已有成员的本场昵称、头像、标签。不改 role，不增删其他人。不是本场成员 → 400。不改归属人、不触发风控回 pending、不同步微吼。',
        operationId: 'updateActivityMember',
        security: bearer,
        parameters: [activityId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/UpdateActivityMemberRequest',
              },
              example: {
                id: 10001,
                nickname: '云奎',
                avatarUrl: 'https://cdn.example/a.jpg',
                tag: '讲师',
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseMembers'),
      },
    },
    '/api/v1/activities/members/add/{id}': {
      post: {
        tags: ['活动管理'],
        summary: '添加开播成员',
        description:
          '只插入请求里的人，不删除、不修改已有成员。已是本场成员、同一 id 重复、或加上已有人数后某角色超过 20 → 400。已绑报备时不能新增 role=1。不改归属人、不触发风控回 pending、不同步微吼。',
        operationId: 'addActivityMembers',
        security: bearer,
        parameters: [activityId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/AddActivityMembersRequest',
              },
              example: {
                members: [
                  {
                    id: 10002,
                    role: 3,
                    nickname: '小助',
                    avatarUrl: 'https://cdn.example/a.jpg',
                    tag: '助教',
                  },
                ],
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseMembers'),
      },
    },
    '/api/v1/activities/members/delete/{id}': {
      delete: {
        tags: ['活动管理'],
        summary: '删除开播成员',
        description:
          '路径 id 是活动 UUID。只删 body.ids 里的员工，其余不动。同一 id 重复、不是本场成员、删完没有主播、或已绑报备时删主播 → 400。不改归属人、不触发风控回 pending、不同步微吼。',
        operationId: 'deleteActivityMembers',
        security: bearer,
        parameters: [activityId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/DeleteActivityMembersRequest',
              },
              example: { ids: [10002] },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseMembers'),
      },
    },
    '/api/v1/activities/members/accept/{id}': {
      post: {
        tags: ['活动管理'],
        summary: '接受角色邀约',
        description:
          '微信邀约：当前登录员工加入本场指定开播角色；邀约人只校验不落库。',
        operationId: 'acceptActivityMember',
        security: bearer,
        parameters: [activityId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/AcceptActivityMemberRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseAcceptMember'),
      },
    },
    '/api/v1/activities/push-stream/{id}': {
      get: {
        tags: ['活动管理'],
        summary: '获取 rtmp 推流地址（第三方推流到直播间）',
        description:
          '只读。管理后台「第三方推流到直播间」页：服务器 + 串流密钥。已软删 → 400。非 vhall 或微吼未接入 → 501。不返回密钥或原始响应。',
        operationId: 'getActivityPushStream',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponsePushStream'),
      },
    },
    '/api/v1/activities/stream-status/{id}': {
      get: {
        tags: ['活动管理'],
        summary: '查询推流状态（是否检测到推流）',
        description:
          '只读。管理后台「推流状态」刷新。status：0 未检测到推流 / 1 有流。已软删 → 400。非 vhall 或微吼未接入 → 501。不返回密钥或原始响应。',
        operationId: 'getActivityStreamStatus',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseStreamStatus'),
      },
    },
    '/api/v1/activities/start/{id}': {
      post: {
        tags: ['活动管理'],
        summary: '开始直播（第三方推流 rtmp 按钮）',
        description:
          '无 body。与 /activities/end/{id} 对称。对应管理后台「第三方推流到直播间」页的「开始直播」：OBS 等三方推流真正开播（微吼 start_type=4），不是网页主持人开播。本站活动即直播，path 不嵌 live。已软删 → 400；非 vhall → 501。成功 data 为 {id, state}，state 多为 1。',
        operationId: 'startActivity',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseLiveStart'),
      },
    },
    '/api/v1/activities/stats/{id}': {
      get: {
        tags: ['统计'],
        summary: '观看概览',
        operationId: 'getActivityStats',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseStats'),
      },
    },
    '/api/v1/activities/stats/online/{id}': {
      get: {
        tags: ['统计'],
        summary: '在线趋势（微吼对照，分钟级）',
        description: 'startTime 与 endTime 跨度必须 ≤1 天。',
        operationId: 'getActivityOnlineTrend',
        security: bearer,
        parameters: [
          activityId,
          {
            name: 'startTime',
            in: 'query',
            required: true,
            schema: { type: 'string', example: '2026-09-17 00:00:00' },
          },
          {
            name: 'endTime',
            in: 'query',
            required: true,
            schema: { type: 'string', example: '2026-09-17 23:59:59' },
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseOnlineTrend'),
      },
    },
    '/api/v1/activities/presence-trend/{id}': {
      get: {
        tags: ['统计'],
        summary: '进出三线走势（管理端）',
        description: '中控台三线图（D110）；数据来自 room_presence_event。',
        operationId: 'getActivityPresenceTrend',
        security: bearer,
        parameters: [
          activityId,
          {
            name: 'startTime',
            in: 'query',
            required: true,
            schema: { type: 'string' },
          },
          {
            name: 'endTime',
            in: 'query',
            required: true,
            schema: { type: 'string' },
          },
          {
            name: 'bucketSeconds',
            in: 'query',
            schema: {
              type: 'integer',
              default: 60,
              enum: [60],
              description: '60 秒',
            },
          },
        ],
        responses: ok200('#/components/schemas/ApiResponsePresenceTrend'),
      },
    },
    '/api/v1/activities/user-data/{id}': {
      get: {
        tags: ['统计'],
        summary: '直播设置 · 用户数据汇总',
        description:
          '本站三表汇总。summary 恒等于 rows 中 type=live；rows 固定 live_replay → live → replay。',
        operationId: 'getActivityUserData',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseActivityUserData'),
      },
    },
    '/api/v1/activities/attendees/{id}': {
      get: {
        tags: ['直播中控台'],
        summary: '中控台进出名单',
        description:
          'admin-web 中控台名单（D110）。admin JWT。原 live GET /watch/attendees 已删除。',
        operationId: 'listActivityAttendees',
        security: bearer,
        parameters: [
          activityId,
          {
            name: 'tab',
            in: 'query',
            schema: {
              type: 'string',
              enum: ['all', 'online', 'offline'],
              default: 'all',
              description: 'all 全部 / online 在线 / offline 离线',
            },
          },
          ...pageParams,
          { name: 'keyword', in: 'query', schema: { type: 'string' } },
        ],
        responses: ok200('#/components/schemas/ApiResponseAttendeeList'),
      },
    },
    '/api/v1/employees': {
      get: {
        tags: ['员工组织'],
        summary: '归属人 / 主播候选',
        operationId: 'listEmployeeCandidates',
        security: bearer,
        parameters: [
          {
            name: 'keyword',
            in: 'query',
            schema: { type: 'string' },
            description: '按 user_name 包含匹配；最多 20 条',
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseEmployees'),
      },
    },
    '/api/v1/org/departments/tree': {
      get: {
        tags: ['员工组织'],
        summary: '部门树',
        operationId: 'getOrgDepartmentTree',
        security: bearer,
        responses: ok200('#/components/schemas/ApiResponseOrgDeptTree'),
      },
    },
    '/api/v1/org/employees': {
      get: {
        tags: ['员工组织'],
        summary: '部门下员工',
        operationId: 'listOrgEmployees',
        security: bearer,
        parameters: [
          {
            name: 'deptId',
            in: 'query',
            schema: { type: 'integer', format: 'int64' },
            description: '部门 id；不传=全部；有值不含下级',
          },
          { name: 'userName', in: 'query', schema: { type: 'string' } },
          {
            name: 'roleId',
            in: 'query',
            schema: { type: 'integer', format: 'int64' },
            description: '角色 id；不传不过滤；≤0 → 400',
          },
          ...pageParams,
        ],
        responses: ok200('#/components/schemas/ApiResponseOrgEmployees'),
      },
    },
    '/api/v1/org/employees/live-enabled/{id}': {
      put: {
        tags: ['员工组织'],
        summary: '直播账号启停',
        operationId: 'setEmployeeLiveEnabled',
        security: bearer,
        parameters: [
          {
            name: 'id',
            in: 'path',
            required: true,
            schema: { type: 'integer', format: 'int64' },
            description: '账号 rs_mainkey_id（路径文本，语义为数字）',
          },
        ],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/LiveEnabledRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseLiveEnabled'),
      },
    },
    '/api/v1/roles/list': {
      get: {
        tags: ['员工组织'],
        summary: '在用角色列表',
        operationId: 'listRoles',
        security: bearer,
        responses: ok200('#/components/schemas/ApiResponseOrgRoles'),
      },
    },
    '/api/v1/roles/detail/{id}': {
      get: {
        tags: ['员工组织'],
        summary: '角色详情（成员 + 菜单）',
        operationId: 'getRoleDetail',
        security: bearer,
        parameters: [
          {
            name: 'id',
            in: 'path',
            required: true,
            schema: { type: 'integer', format: 'int64' },
            description: '角色 rs_mainkey_id',
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseOrgRoleDetail'),
      },
    },
    '/api/v1/roles/save-menus': {
      post: {
        tags: ['员工组织'],
        summary: '保存角色本站菜单',
        description: '整表替换 live_role_menu。id 在 body。',
        operationId: 'saveRoleMenus',
        security: bearer,
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/RoleMenusRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseOrgRoleDetail'),
      },
    },
    '/api/v1/live-reports/create': {
      post: {
        tags: ['直播报备'],
        summary: '新建并提交报备',
        description: '报备 ID 由服务端生成，请求体不含 id。成功返回 id 与 status。',
        operationId: 'createLiveReport',
        security: bearer,
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/LiveReportSaveRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseLiveReportResult'),
      },
    },
    '/api/v1/live-reports/list': {
      get: {
        tags: ['直播报备'],
        summary: '报备列表',
        description: '返回计划时间与查询区间有交集的数据。',
        operationId: 'listLiveReports',
        security: bearer,
        parameters: [
          {
            name: 'page',
            in: 'query',
            schema: { type: 'integer', default: 1, minimum: 1 },
          },
          {
            name: 'pageSize',
            in: 'query',
            schema: { type: 'integer', default: 10, minimum: 1, maximum: 100 },
          },
          { name: 'title', in: 'query', schema: { type: 'string' } },
          { name: 'hostName', in: 'query', schema: { type: 'string' } },
          {
            name: 'status',
            in: 'query',
            schema: { $ref: '#/components/schemas/LiveReportStatus' },
          },
          {
            name: 'startFrom',
            in: 'query',
            schema: { type: 'string' },
            description:
              '计划时间筛选起点，yyyy-MM-dd HH:mm:ss；与 startTo 组成区间，命中条件为任一段 planTimes 与区间相交（非外包 min/max）',
          },
          {
            name: 'startTo',
            in: 'query',
            schema: { type: 'string' },
            description:
              '计划时间筛选终点，yyyy-MM-dd HH:mm:ss；与 startFrom 组成区间，命中条件为任一段 planTimes 与区间相交（非外包 min/max）',
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseLiveReportList'),
      },
    },
    '/api/v1/live-reports/detail/{id}': {
      get: {
        tags: ['直播报备'],
        summary: '报备详情',
        operationId: 'getLiveReport',
        security: bearer,
        parameters: [liveReportId],
        responses: ok200('#/components/schemas/ApiResponseLiveReportDetail'),
      },
    },
    '/api/v1/live-reports/update/{id}': {
      put: {
        tags: ['直播报备'],
        summary: '修改报备',
        description:
          '驳回态提交完整字段并回到 pending。已通过且未绑定直播时只允许在 planTimes 尾部追加。其他状态 400。',
        operationId: 'updateLiveReport',
        security: bearer,
        parameters: [liveReportId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/LiveReportUpdateRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseLiveReportResult'),
      },
    },
    '/api/v1/live-reports/audit/{id}': {
      post: {
        tags: ['直播报备'],
        summary: '审核报备',
        description: 'pending / passed / rejected 均可审，可反复改结论（只追加记录）。驳回 reason 必填，最多 500 字。',
        operationId: 'auditLiveReport',
        security: bearer,
        parameters: [liveReportId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/LiveReportAuditRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseLiveReportResult'),
      },
    },
    '/api/v1/live-reports/audits/{id}': {
      get: {
        tags: ['直播报备'],
        summary: '报备审核记录',
        description: '按时间倒序。action 为 submit / resubmit / audit / append。',
        operationId: 'listLiveReportAudits',
        security: bearer,
        parameters: [liveReportId],
        responses: ok200('#/components/schemas/ApiResponseLiveReportAudits'),
      },
    },
    '/api/v1/courses/create': {
      post: {
        tags: ['课程'],
        summary: '新建课程',
        description: [
          '新建课程并写入 course 表。成功后 riskStatus=pending、shelfStatus=pending，并写一条审核流水 action=submit。',
          '售卖字段请勿在创建请求中提交：服务端默认 sellAlone=true、sellType=free、accessValidityType=long、validityLabel=「长期有效」；改售卖请用 PUT /courses/sales/{id}。',
          '响应 data 含新建课程 id。',
        ].join(''),
        operationId: 'createCourse',
        security: bearer,
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/CreateCourseRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseId'),
      },
    },
    '/api/v1/courses/update/{id}': {
      post: {
        tags: ['课程'],
        summary: '更新课程基本信息',
        description: [
          '更新课程基本信息（标题、封面、简介、归属人、课件等），Body 字段与创建同形；不改售卖设置。',
          '当前 riskStatus=pending（审核中）→ 400。riskStatus 为 passed 或 rejected 时，保存后改回 pending 并写一条 action=submit。',
          'materials 传空数组表示清空课件绑定。课程不存在或已软删 → 400。',
        ].join(''),
        operationId: 'updateCourse',
        security: bearer,
        parameters: [courseId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/CreateCourseRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseId'),
      },
    },
    '/api/v1/courses/list': {
      get: {
        tags: ['课程'],
        summary: '课程列表',
        description:
          '分页查询未软删课程。支持按标题模糊、上架状态、风控状态、归属人姓名精确筛选，以及按权益用户数/累计用户数/总销量/最近更新时间排序。',
        operationId: 'listCourses',
        security: bearer,
        parameters: [
          ...pageParams,
          {
            name: 'keyword',
            in: 'query',
            schema: { type: 'string' },
            description: '标题模糊',
          },
          {
            name: 'shelfStatus',
            in: 'query',
            schema: { $ref: '#/components/schemas/ShelfStatus' },
          },
          {
            name: 'ownerName',
            in: 'query',
            schema: { type: 'string' },
            description: '归属人姓名精确，命中任一在职归属人',
          },
          {
            name: 'riskStatus',
            in: 'query',
            schema: { $ref: '#/components/schemas/RiskStatus' },
          },
          {
            name: 'sort',
            in: 'query',
            schema: {
              type: 'string',
              enum: [
                'entitledUserCount',
                'totalUserCount',
                'salesCount',
                'updatedTime',
              ],
              default: 'updatedTime',
              description:
                'entitledUserCount 权益用户数 / totalUserCount 累计用户数 / salesCount 总销量 / updatedTime 最近更新时间',
            },
          },
          {
            name: 'order',
            in: 'query',
            schema: {
              type: 'string',
              enum: ['asc', 'desc'],
              default: 'desc',
              description: 'asc 升序 / desc 降序',
            },
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseCourseList'),
      },
    },
    '/api/v1/courses/detail/{id}': {
      get: {
        tags: ['课程'],
        summary: '课程详情',
        description:
          '返回课程详情（含售卖字段、课件、归属人等）。课程不存在或已软删 → 400。sellType=encrypted 时 password 回显 AES 解密后的 6 位明文，仅供管理端编辑。',
        operationId: 'getCourse',
        security: bearer,
        parameters: [courseId],
        responses: ok200('#/components/schemas/ApiResponseCourseDetail'),
      },
    },
    '/api/v1/courses/delete/{id}': {
      delete: {
        tags: ['课程'],
        summary: '软删课程',
        description:
          '软删课程：写入 course.deleted_time。不要求内容数或总销量为 0。课程不存在或已软删 → 400。',
        operationId: 'deleteCourse',
        security: bearer,
        parameters: [courseId],
        responses: ok200('#/components/schemas/ApiResponseId'),
      },
    },
    '/api/v1/courses/sales/{id}': {
      put: {
        tags: ['课程'],
        summary: '课程售卖设置',
        description: [
          '更新课程售卖设置，只写本站 course 表。',
          'sellType 仅允许 free（免费）/ encrypted（加密）/ specified（指定学员）；不接受 paid（课程无付费售卖；传 paid →「sellType 无效」）。',
          'sellAlone 必填。sellType=free 时服务端强制 accessValidityType=long；非 free 时必填 long / days / fixed。',
          'days 时 accessValidityDays 必填且 ≥1；fixed 时 accessValidityFixedTime 必填（yyyy-MM-dd HH:mm:ss）。',
          'encrypted 时 password 必填（恰好 6 位数字明文），服务端 AES 加密写入 course.sell_password_cipher；非加密时清空密文。',
          '保存后按有效期重算写入 course.validity_label（免费或 long →「长期有效」；days →「{N}天有效」如「7天有效」；fixed →「{yyyy-MM-dd HH:mm:ss}到期」如「2026-11-14 00:00:00到期」）。',
          '学员到期回写（course_user.entitlement_expire_time）：当有效期类型从非 days 改为 days 时，对本课该字段仍为空的行，按各自 created_time 加 N 天写入；',
          '当保存为 fixed 时，对本课该字段仍为空的行写入该固定时刻；已有到期时间的行一律不改（含 days 改天数、再次改固定时刻、改回长期）。',
          '响应 data：id、sellType、validityLabel。课程不存在或已软删 → 400。本接口不实现学员侧观看门禁。',
        ].join(''),
        operationId: 'updateCourseSales',
        security: bearer,
        parameters: [courseId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/CourseSalesUpdateRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseCourseSales'),
      },
    },
    '/api/v1/courses/users/list/{courseId}': {
      get: {
        tags: ['课程'],
        summary: '课程用户列表',
        description: [
          '分页查询本课学员（course_user）。可按昵称/用户ID/手机号关键词筛选。',
          'Query entitlementState 可选：valid（有效）/ expired（已到期）/ invalid（已失效）；省略则不过滤。',
          '计算态：entitlement_status=0 → invalid；否则当课程售卖为 days 或 fixed、且行上 entitlement_expire_time 非空且已过当前时间 → expired；其余为 valid（售卖长期不算已到期）。',
          '到期列展示用响应字段 expireDisplay（售卖长期为「长期」）。',
        ].join(''),
        operationId: 'listCourseUsers',
        security: bearer,
        parameters: [
          {
            name: 'courseId',
            in: 'path',
            required: true,
            schema: { ...BUSINESS_UUID },
            description: '本站课程 id（D74）',
          },
          ...pageParams,
          {
            name: 'keywordType',
            in: 'query',
            schema: {
              type: 'string',
              enum: ['nickname', 'id', 'phone'],
              default: 'nickname',
              description: 'nickname 昵称 / id 用户ID / phone 手机号',
            },
          },
          {
            name: 'keyword',
            in: 'query',
            schema: { type: 'string' },
            description: '关键词；配合 keywordType',
          },
          {
            name: 'entitlementState',
            in: 'query',
            schema: { $ref: '#/components/schemas/CourseEntitlementState' },
            description:
              '权益计算态筛选：valid 有效 / expired 已到期 / invalid 已失效；省略=全部',
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseCourseUserList'),
      },
    },
    '/api/v1/courses/users/entitlement/{rowId}': {
      put: {
        tags: ['课程'],
        summary: '开关课程用户观看权益',
        description:
          '修改指定 course_user 行的 entitlement_status：1 有效 / 0 已取消。不改 entitlement_expire_time。行不存在 → 400。',
        operationId: 'updateCourseUserEntitlement',
        security: bearer,
        parameters: [
          {
            name: 'rowId',
            in: 'path',
            required: true,
            schema: { type: 'integer', format: 'int64' },
            description: 'course_user 行 id',
          },
        ],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/CourseUserEntitlementRequest',
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseCourseUserEntitlement'),
      },
    },
    '/api/v1/courses/users/expire/{rowId}': {
      put: {
        tags: ['课程'],
        summary: '延迟用户时间 / 自定义到期',
        description: [
          '手工改写指定 course_user 行的 entitlement_expire_time；Body expireTime 必填，格式 yyyy-MM-dd HH:mm:ss。',
          '不改 entitlement_status。若课程售卖仍为长期（accessValidityType=long），列表 expireDisplay 仍展示「长期」（忽略行上到期时间）。',
          '行不存在或时间格式非法 → 400。',
        ].join(''),
        operationId: 'updateCourseUserExpire',
        security: bearer,
        parameters: [
          {
            name: 'rowId',
            in: 'path',
            required: true,
            schema: { type: 'integer', format: 'int64' },
            description: 'course_user 行 id',
          },
        ],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/CourseUserExpireRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseCourseUserExpire'),
      },
    },
    '/api/v1/courses/users/create/{courseId}': {
      post: {
        tags: ['课程'],
        summary: '单个添加学员',
        description: [
          '向本课添加一名学员。Body 须二选一提供 phone 或 viewerId；选填 expireTime（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日则存当日 00:00:00）。',
          '本课无该观众行时：新建 course_user，entitlement_status=1、acquire_way=import、写导入人；有 expireTime 则写入该时刻，否则按课程售卖规则计算到期；course.total_user_count +1。',
          '已有行：仅补写空的导入人，不改 entitlement_expire_time；响应 created=false。',
        ].join(''),
        operationId: 'createCourseUser',
        security: bearer,
        parameters: [
          {
            name: 'courseId',
            in: 'path',
            required: true,
            schema: { ...BUSINESS_UUID },
            description: '本站课程 id（D74）',
          },
        ],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/CourseUserCreateRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseCourseUserCreate'),
      },
    },
    '/api/v1/courses/users/import/{courseId}': {
      post: {
        tags: ['课程'],
        summary: 'CSV 批量导入学员',
        description: [
          'multipart/form-data 批量导入学员：必填 file（仅 CSV，首列/列名「手机号」）；选填 expireTime（整批新建行共用；yyyy-MM-dd 或带时分秒）。',
          '新建行写入 course_user（acquire_way=import），到期用自定义或按课程售卖计算；已有行不改 entitlement_expire_time。',
          '同时写入 course_user_import 导入历史。响应含 importId 与 total/created/updated/skipped/failed 等统计。',
        ].join(''),
        operationId: 'importCourseUsers',
        security: bearer,
        parameters: [
          {
            name: 'courseId',
            in: 'path',
            required: true,
            schema: { ...BUSINESS_UUID },
            description: '本站课程 id（D74）',
          },
        ],
        requestBody: {
          required: true,
          content: {
            'multipart/form-data': {
              schema: {
                type: 'object',
                required: ['file'],
                properties: {
                  file: {
                    type: 'string',
                    format: 'binary',
                    description: 'CSV 文件；首列/列名「手机号」',
                  },
                  expireTime: {
                    type: 'string',
                    description:
                      '选填；仅作用于新建 course_user 行；yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日则存当日 00:00:00；空则按课程售卖规则计算到期',
                  },
                },
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseCourseUserImport'),
      },
    },
    '/api/v1/courses/users/import-by-tags/estimate/{courseId}': {
      post: {
        tags: ['课程'],
        summary: '标签导入预计人数',
        description:
          '按观众全局标签预估将导入人数：Body tagIds 满足其一即计入，结果去重；只算人数、不写入 course_user。响应 data.total 为预计人数。',
        operationId: 'estimateCourseUsersByTags',
        security: bearer,
        parameters: [
          {
            name: 'courseId',
            in: 'path',
            required: true,
            schema: { ...BUSINESS_UUID },
            description: '本站课程 id（D74）',
          },
        ],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/CourseUserImportByTagsRequest',
              },
            },
          },
        },
        responses: ok200(
          '#/components/schemas/ApiResponseCourseUserImportByTagsEstimate',
        ),
      },
    },
    '/api/v1/courses/users/import-by-tags/{courseId}': {
      post: {
        tags: ['课程'],
        summary: '按标签导入学员',
        description: [
          '按观众全局标签把命中观众 upsert 进本课 course_user（非 activity_audience）。Body：tagIds 必填，expireTime 选填（仅新建行）。',
          '本课无行 → 新建 entitlement_status=1、acquire_way=import、写导入人、到期按自定义或售卖规则，total_user_count +1；',
          '已有行且导入人为空 → 补写导入人；已有导入人 → 跳过。不改已有行的权益与 entitlement_expire_time；不写 course_user_import 历史。',
        ].join(''),
        operationId: 'importCourseUsersByTags',
        security: bearer,
        parameters: [
          {
            name: 'courseId',
            in: 'path',
            required: true,
            schema: { ...BUSINESS_UUID },
            description: '本站课程 id（D74）',
          },
        ],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/CourseUserImportByTagsRequest',
              },
            },
          },
        },
        responses: ok200(
          '#/components/schemas/ApiResponseCourseUserImportByTags',
        ),
      },
    },
    '/api/v1/courses/users/import-template': {
      get: {
        tags: ['课程'],
        summary: '课程用户导入模板',
        description: '下载 CSV 导入模板（UTF-8 BOM），表头为「手机号」。',
        operationId: 'downloadCourseUserImportTemplate',
        security: bearer,
        responses: csvOk200('UTF-8 BOM CSV，表头：手机号'),
      },
    },
    '/api/v1/courses/users/imports/list/{courseId}': {
      get: {
        tags: ['课程'],
        summary: '课程用户导入历史',
        description:
          '返回本课 CSV 导入历史（course_user_import），最多 100 条，按时间倒序。',
        operationId: 'listCourseUserImports',
        security: bearer,
        parameters: [
          {
            name: 'courseId',
            in: 'path',
            required: true,
            schema: { ...BUSINESS_UUID },
            description: '本站课程 id（D74）',
          },
        ],
        responses: ok200(
          '#/components/schemas/ApiResponseCourseUserImportHistory',
        ),
      },
    },
    '/api/v1/courses/users/imports/failures/{importId}': {
      get: {
        tags: ['课程'],
        summary: '课程用户导入失败明细 CSV',
        description:
          '按导入批次 download 失败明细 CSV，列为：行号、手机号、原因。批次不存在 → 400。',
        operationId: 'downloadCourseUserImportFailures',
        security: bearer,
        parameters: [
          {
            name: 'importId',
            in: 'path',
            required: true,
            schema: { type: 'integer', format: 'int64' },
            description: '导入批次 id',
          },
        ],
        responses: csvOk200('失败明细 CSV：行号,手机号,原因'),
      },
    },
    '/api/v1/courses/users/export/{courseId}': {
      get: {
        tags: ['课程'],
        summary: '导出课程学员 CSV',
        description: [
          '按与用户列表相同的筛选条件导出本课全部学员 CSV（UTF-8 BOM），不分页（不含 page/pageSize）。',
          '列：学员、用户ID、手机号、标签、小节完成率、权益状态、权益获取方式、权益首次获取时间、权益到期时间。',
        ].join(''),
        operationId: 'exportCourseUsers',
        security: bearer,
        parameters: [
          {
            name: 'courseId',
            in: 'path',
            required: true,
            schema: { ...BUSINESS_UUID },
            description: '本站课程 id（D74）',
          },
          {
            name: 'keywordType',
            in: 'query',
            schema: {
              type: 'string',
              enum: ['nickname', 'id', 'phone'],
              default: 'nickname',
              description: 'nickname 昵称 / id 用户ID / phone 手机号',
            },
          },
          {
            name: 'keyword',
            in: 'query',
            schema: { type: 'string' },
            description: '关键词',
          },
          {
            name: 'entitlementState',
            in: 'query',
            schema: { $ref: '#/components/schemas/CourseEntitlementState' },
            description: '可选；省略=全部',
          },
        ],
        responses: csvOk200(
          '学员 CSV：学员、用户ID、手机号、标签、小节完成率、权益状态、权益获取方式、权益首次获取时间、权益到期时间',
        ),
      },
    },
    '/api/v1/courses/shelf/{id}': {
      post: {
        tags: ['课程'],
        summary: '课程上架 / 下架',
        description: [
          'Body listed=true 上架 / false 下架。',
          '上架要求 riskStatus=passed 且当前 shelfStatus 为 pending 或 off，成功后写入 listedTime；',
          '下架仅当当前已上架（on）。shelfStatus=banned（强制封禁）不可上下架。条件不满足或课程不存在 → 400。',
        ].join(''),
        operationId: 'shelfCourse',
        security: bearer,
        parameters: [courseId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/ShelfRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseShelf'),
      },
    },
    '/api/v1/courses/audits/{id}': {
      get: {
        tags: ['课程'],
        summary: '课程审核记录',
        description:
          '返回本课审核流水，按时间倒序。action 为 submit（提交/重提）或 audit（审核）；submit 记录不返回 result / reason。',
        operationId: 'listCourseAudits',
        security: bearer,
        parameters: [courseId],
        responses: ok200('#/components/schemas/ApiResponseAudits'),
      },
    },
    '/api/v1/courses/audit/{id}': {
      post: {
        tags: ['课程'],
        summary: '审核课程',
        description: [
          '审核本课：通过（passed）无需理由；驳回（rejected）须填理由，并把 shelfStatus 改为 banned。',
          '审核通过时若当前为 banned 则回到 pending，否则上架不动（D109 不再读 autoList）。',
          '写一条 action=audit 流水。课程不存在或状态不允许审核 → 400。',
        ].join(''),
        operationId: 'auditCourse',
        security: bearer,
        parameters: [courseId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/AuditSubmitRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseAuditSubmit'),
      },
    },
    '/api/v1/courses/catalog/list/{courseId}': {
      get: {
        tags: ['课程'],
        summary: '课程目录节点列表',
        description: [
          '返回本课目录节点扁平列表（子课程 / 章节 / 小节）。',
          '同一父节点下：若全部 sortOrder=0，默认顺序为子课程→章节→小节；若存在小节自定义序，则可与章节交错。',
          '子课程节点附带 chapterCount、lessonCount。',
        ].join(''),
        operationId: 'listCourseCatalog',
        security: bearer,
        parameters: [courseIdPath],
        responses: ok200('#/components/schemas/ApiResponseCourseCatalogList'),
      },
    },
    '/api/v1/courses/catalog/used-contents/{courseId}': {
      get: {
        tags: ['课程'],
        summary: '本课已占用内容',
        description:
          '返回本课目录中已绑定的直播 id 列表（lives）与素材 id 列表（materials），供管理端「绑定内容」弹窗禁用已占用项，避免重复绑定。',
        operationId: 'listCourseCatalogUsedContents',
        security: bearer,
        parameters: [courseIdPath],
        responses: ok200(
          '#/components/schemas/ApiResponseCourseCatalogUsedContents',
        ),
      },
    },
    '/api/v1/courses/catalog/lessons/bind-lives/{courseId}': {
      post: {
        tags: ['课程'],
        summary: '绑定直播为小节',
        description: [
          '将多场直播批量建成小节（contentKind=1）；小节标题取直播名并截断至 ≤30 字。',
          '课程已有子课程后，小节的 parentId 不可为 0（须挂在章节或子课程下）。同一直播已在本课占用 → 400。',
          '副作用：写入小节并重算 course.content_count。成功仅返回 code/msg，无 data（前端自行刷新目录与课程详情）。',
        ].join(''),
        operationId: 'bindCourseCatalogLives',
        security: bearer,
        parameters: [courseIdPath],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/CourseCatalogBindLivesRequest',
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseBase'),
      },
    },
    '/api/v1/courses/catalog/lessons/bind-materials/{courseId}': {
      post: {
        tags: ['课程'],
        summary: '绑定素材为小节',
        description: [
          '将多个素材批量建成小节；contentKind 由素材 type 映射（image→2 / video→3 / document→4 / article→5）。',
          '同一素材已在本课占用 → 400。副作用与成功回包同 bind-lives：重算 content_count；成功仅 code/msg，无 data。',
        ].join(''),
        operationId: 'bindCourseCatalogMaterials',
        security: bearer,
        parameters: [courseIdPath],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/CourseCatalogBindMaterialsRequest',
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseBase'),
      },
    },
    '/api/v1/courses/catalog/lessons/update/{courseId}': {
      post: {
        tags: ['课程'],
        summary: '改小节标题',
        description:
          '修改指定小节的标题（1～30 字）；小节不存在、已软删或节点不是小节 → 400。成功返回该小节节点。',
        operationId: 'updateCourseCatalogLesson',
        security: bearer,
        parameters: [courseIdPath],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/CourseCatalogLessonUpdateRequest',
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseCourseCatalogNode'),
      },
    },
    '/api/v1/courses/catalog/chapters/create/{courseId}': {
      post: {
        tags: ['课程'],
        summary: '新建章节',
        description: '在本课目录下新建章节节点；成功后返回更新后的目录列表。',
        operationId: 'createCourseCatalogChapter',
        security: bearer,
        parameters: [courseIdPath],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/CourseCatalogChapterCreateRequest',
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseCourseCatalogList'),
      },
    },
    '/api/v1/courses/catalog/chapters/update/{courseId}': {
      post: {
        tags: ['课程'],
        summary: '改章节标题',
        description: '修改指定章节的标题；章节不存在或已软删 → 400。',
        operationId: 'updateCourseCatalogChapter',
        security: bearer,
        parameters: [courseIdPath],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/CourseCatalogChapterUpdateRequest',
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseCourseCatalogList'),
      },
    },
    '/api/v1/courses/catalog/chapters/delete/{courseId}': {
      post: {
        tags: ['课程'],
        summary: '软删章节',
        description: [
          '软删指定章节。keepContents=true 时，可将章节下内容迁移到 targetChapterId；',
          'keepContents=false 时级联软删下属小节，并重算课程 contentCount。',
        ].join(''),
        operationId: 'deleteCourseCatalogChapter',
        security: bearer,
        parameters: [courseIdPath],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/CourseCatalogChapterDeleteRequest',
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseCourseCatalogDelete'),
      },
    },
    '/api/v1/courses/catalog/subcourses/create/{courseId}': {
      post: {
        tags: ['课程'],
        summary: '新建子课程',
        description: [
          '在课程根下新建子课程节点。',
          '若这是首个子课程，且根下已有章节/小节，须传 moveRootContents=true 把原有根内容迁入该子课程，否则 → 400。',
        ].join(''),
        operationId: 'createCourseCatalogSubcourse',
        security: bearer,
        parameters: [courseIdPath],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/CourseCatalogSubcourseCreateRequest',
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseCourseCatalogList'),
      },
    },
    '/api/v1/courses/catalog/subcourses/update/{courseId}': {
      post: {
        tags: ['课程'],
        summary: '改子课程名称与封面',
        description: '修改指定子课程的名称与封面；子课程不存在或已软删 → 400。',
        operationId: 'updateCourseCatalogSubcourse',
        security: bearer,
        parameters: [courseIdPath],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/CourseCatalogSubcourseUpdateRequest',
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseCourseCatalogList'),
      },
    },
    '/api/v1/courses/catalog/subcourses/delete/{courseId}': {
      post: {
        tags: ['课程'],
        summary: '软删子课程',
        description: [
          '软删指定子课程。keepContents=true 时，可将下属内容迁移到 targetSubcourseId；',
          'keepContents=false 时级联软删下属内容，并重算课程 contentCount。',
        ].join(''),
        operationId: 'deleteCourseCatalogSubcourse',
        security: bearer,
        parameters: [courseIdPath],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/CourseCatalogSubcourseDeleteRequest',
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseCourseCatalogDelete'),
      },
    },
    '/api/v1/courses/catalog/subcourses/reorder/{courseId}': {
      post: {
        tags: ['课程'],
        summary: '重排子课程',
        description: [
          '按 Body ids 顺序重排本课全部未软删子课程：ids 必须与当前集合完全一致（不多不少），并写入 sortOrder=1..n。',
          '集合不一致 → 400。成功仅返回 code/msg，无 data（前端自行刷新目录列表）。',
        ].join(''),
        operationId: 'reorderCourseCatalogSubcourses',
        security: bearer,
        parameters: [courseIdPath],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/CourseCatalogSubcourseReorderRequest',
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseBase'),
      },
    },
    '/api/v1/courses/catalog/reorder/{courseId}': {
      post: {
        tags: ['课程'],
        summary: '目录排序弹窗统一保存',
        description: [
          '统一保存目录排序。Body mode=chapters（排章节）或 lessons（排小节）；',
          '自定义排序写入 sortOrder=1..n，未出现在排序列表中的节点保留 sortOrder=0（未排序）。',
          '成功仅返回 code/msg，无 data（前端自行刷新目录列表）。',
        ].join(''),
        operationId: 'reorderCourseCatalog',
        security: bearer,
        parameters: [courseIdPath],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/CourseCatalogReorderRequest',
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseBase'),
      },
    },

    '/api/v1/materials/audits/{id}': {
      get: {
        tags: ['素材中心'],
        summary: '素材审核记录',
        operationId: 'listMaterialAudits',
        security: bearer,
        parameters: [materialId],
        responses: ok200('#/components/schemas/ApiResponseMaterialAudits'),
      },
    },
    '/api/v1/materials/vhall-transcode/{id}': {
      post: {
        tags: ['素材中心'],
        summary: '上传微吼转码',
        operationId: 'materialVhallTranscode',
        security: bearer,
        parameters: [materialId],
        responses: ok200('#/components/schemas/ApiResponseMaterialItem'),
      },
    },
    '/api/v1/activities/passcode/{id}': {
      get: {
        tags: ['活动管理'],
        summary: '查本场口令',
        operationId: 'getActivityPasscode',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponsePasscode'),
      },
    },
    '/api/v1/activities/passcode/reset/{id}': {
      post: {
        tags: ['活动管理'],
        summary: '重置口令',
        operationId: 'resetActivityPasscode',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponsePasscode'),
      },
    },
    '/api/v1/outside/host/passcode/enter': {
      post: {
        tags: ['活动管理'],
        summary: '口令进房',
        description: '不鉴权；H5/App 口令开播共用。',
        operationId: 'passcodeHostEnter',
        security: [],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/PasscodeHostEnterRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponsePasscodeHostEnter'),
      },
    },
    '/api/v1/activities/audiences/list/{id}': {
      get: {
        tags: ['活动管理'],
        summary: '用户运营名单',
        operationId: 'listActivityAudiences',
        security: bearer,
        parameters: [activityId,
          ...pageParams,
          { name: 'keywordType', in: 'query', schema: { type: 'string', enum: ['nickname', 'id', 'phone'], default: 'nickname', description: 'nickname 昵称 / id 用户ID / phone 手机号' } },
          { name: 'keyword', in: 'query', schema: { type: 'string' } },
          { name: 'tagIds', in: 'query', schema: { type: 'string' }, description: '逗号分隔标签 id' },
          { name: 'tagMatch', in: 'query', schema: { type: 'string', enum: ['any', 'none', 'all'], default: 'any', description: 'any 满足其一 / none 不包含 / all 全部满足' } },
          { name: 'entitlementStatus', in: 'query', schema: { type: 'integer', enum: [0, 1], description: '0 无权益 / 1 有权益' } },

          { name: 'totalWatchDurationOp', in: 'query', schema: { type: 'string', enum: ['gte', 'lte'], description: 'gte 大于等于 / lte 小于等于' } },
          { name: 'totalWatchDurationSec', in: 'query', schema: { type: 'integer', minimum: 0 } },
          { name: 'liveWatchDurationOp', in: 'query', schema: { type: 'string', enum: ['gte', 'lte'], description: 'gte 大于等于 / lte 小于等于' } },
          { name: 'liveWatchDurationSec', in: 'query', schema: { type: 'integer', minimum: 0 } },
          { name: 'sort', in: 'query', schema: { type: 'string' } },
          { name: 'order', in: 'query', schema: { type: 'string', enum: ['asc', 'desc'], default: 'desc', description: 'asc 升序 / desc 降序' } },
        ],
        responses: ok200('#/components/schemas/ApiResponseActivityAudienceList'),
      },
    },
    '/api/v1/activities/audiences/create/{id}': {
      post: {
        tags: ['活动管理'],
        summary: '添加用户',
        operationId: 'createActivityAudience',
        security: bearer,
        parameters: [activityId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/ActivityAudienceCreateRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseActivityAudienceCreate'),
      },
    },
    '/api/v1/activities/audiences/import/{id}': {
      post: {
        tags: ['活动管理'],
        summary: 'CSV 批量导入',
        operationId: 'importActivityAudiences',
        security: bearer,
        parameters: [activityId],
        requestBody: {
          required: true,
          content: {
            'multipart/form-data': {
              schema: {
                type: 'object',
                required: ['file'],
                properties: {
                  file: { type: 'string', format: 'binary', description: 'CSV 文件' },
                },
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseActivityAudienceImport'),
      },
    },
    '/api/v1/activities/audiences/import-by-tags/estimate/{id}': {
      post: {
        tags: ['活动管理'],
        summary: '标签导入预计人数',
        description: '只算人数、不写入；满足其一去重。',
        operationId: 'estimateActivityAudiencesByTags',
        security: bearer,
        parameters: [activityId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/ActivityAudienceImportByTagsRequest',
              },
            },
          },
        },
        responses: ok200(
          '#/components/schemas/ApiResponseActivityAudienceImportByTagsEstimate',
        ),
      },
    },
    '/api/v1/activities/audiences/import-by-tags/{id}': {
      post: {
        tags: ['活动管理'],
        summary: '按标签导入本场观众',
        description:
          'upsert activity_audience；不写 CSV 导入历史；已有行不改权益。',
        operationId: 'importActivityAudiencesByTags',
        security: bearer,
        parameters: [activityId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/ActivityAudienceImportByTagsRequest',
              },
            },
          },
        },
        responses: ok200(
          '#/components/schemas/ApiResponseActivityAudienceImportByTags',
        ),
      },
    },
    '/api/v1/activities/audiences/import-template': {
      get: {
        tags: ['活动管理'],
        summary: '导入模板',
        operationId: 'downloadActivityAudienceImportTemplate',
        security: bearer,
        responses: csvOk200('UTF-8 BOM CSV，表头：手机号'),
      },
    },
    '/api/v1/activities/audiences/imports/list/{activityId}': {
      get: {
        tags: ['活动管理'],
        summary: '导入历史',
        operationId: 'listActivityAudienceImports',
        security: bearer,
        parameters: [
          {
            name: 'activityId',
            in: 'path',
            required: true,
            schema: { ...BUSINESS_UUID },
            description: '活动 id（D74）',
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseActivityAudienceImportHistory'),
      },
    },
    '/api/v1/activities/audiences/imports/failures/{importId}': {
      get: {
        tags: ['活动管理'],
        summary: '失败明细 CSV',
        operationId: 'downloadActivityAudienceImportFailures',
        security: bearer,
        parameters: [
          {
            name: 'importId',
            in: 'path',
            required: true,
            schema: { type: 'integer', format: 'int64' },
            description: '导入批次 id',
          },
        ],
        responses: csvOk200('失败明细 CSV：行号,手机号,原因'),
      },
    },
    '/api/v1/activities/audiences/export/{id}': {
      get: {
        tags: ['活动管理'],
        summary: '导出用户名单 CSV',
        operationId: 'exportActivityAudiences',
        security: bearer,
        parameters: [activityId,
          ...pageParams,
          { name: 'keywordType', in: 'query', schema: { type: 'string', enum: ['nickname', 'id', 'phone'], default: 'nickname', description: 'nickname 昵称 / id 用户ID / phone 手机号' } },
          { name: 'keyword', in: 'query', schema: { type: 'string' } },
          { name: 'tagIds', in: 'query', schema: { type: 'string' }, description: '逗号分隔标签 id' },
          { name: 'tagMatch', in: 'query', schema: { type: 'string', enum: ['any', 'none', 'all'], default: 'any', description: 'any 满足其一 / none 不包含 / all 全部满足' } },
          { name: 'entitlementStatus', in: 'query', schema: { type: 'integer', enum: [0, 1], description: '0 无权益 / 1 有权益' } },
],
        responses: csvOk200('按 list 同筛选导出 CSV（不分页）'),
      },
    },
    '/api/v1/activities/audiences/entitlement/{rowId}': {
      put: {
        tags: ['活动管理'],
        summary: '开关观看权益',
        operationId: 'updateActivityAudienceEntitlement',
        security: bearer,
        parameters: [
          {
            name: 'rowId',
            in: 'path',
            required: true,
            schema: { type: 'integer', format: 'int64' },
            description: '名单行 id',
          },
        ],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/ActivityAudienceEntitlementRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseActivityAudienceEntitlement'),
      },
    },
    '/api/v1/activities/audiences/comments/{id}': {
      get: {
        tags: ['活动管理'],
        summary: '本场评论列表',
        operationId: 'listActivityAudienceComments',
        security: bearer,
        parameters: [
          activityId,
          ...pageParams,
          { name: 'nickname', in: 'query', schema: { type: 'string' } },
        ],
        responses: ok200('#/components/schemas/ApiResponseActivityAudienceComments'),
      },
    },
    '/api/v1/activities/chat-filter/{id}': {
      get: {
        tags: ['活动管理'],
        summary: '评论管理 / 中控台评论审核 iframe URL',
        description:
          '用户运营 · 评论管理与中控台共用（D34/D110）。仅 provider=vhall，否则 501。省略 email/name 时用当前员工 e{id}@qkzb.com / 姓名。',
        operationId: 'getActivityChatFilter',
        security: bearer,
        parameters: [
          activityId,
          { name: 'email', in: 'query', schema: { type: 'string' } },
          { name: 'name', in: 'query', schema: { type: 'string', maxLength: 30 } },
        ],
        responses: ok200('#/components/schemas/ApiResponseChatFilterAdmin'),
      },
    },
    '/api/v1/activities/console/summary/{id}': {
      get: {
        tags: ['直播中控台'],
        summary: '中控台页壳摘要',
        description:
          'admin-web 单场中控台页壳（D110）。读本库 Activity；admin JWT；无观众售卖门禁；可不做云态对账。字段对齐 /watch/summary。',
        operationId: 'getConsoleSummary',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseConsoleSummary'),
      },
    },
    '/api/v1/activities/console/sdk-sign': {
      get: {
        tags: ['直播中控台'],
        summary: '中控台员工进房 SDK 签名',
        description: [
          '中控台挂微吼观看 SDK（D110）。活动 UUID 用 Query id（同观看 sdk-sign 的 D92 例外）。',
          '员工 id 只从 admin JWT 取；sdk.email 一律 z{员工id}@qkzb.com 并参与签名（与角色 e{id} 错开，避免微吼同邮箱 20005）；sdk.account 为第三方用户唯一标识 z{员工id}（聊天回包未必带回，认人不能只靠它）。',
          '中控台页默认省略 Query nickname（勿写死「助理」）。',
          '昵称（sdk.username / 顶层 nickname）：Query nickname（若传）→ 本场 activity_member.nickname（开播设置·角色设置本场直播昵称）→ 员工姓名 → 助理（≤30）。',
          '头像链接拼接（sdk.head）：底座按本场 activity_member.avatar_url → 员工 headimgurl → user_avatar 取第一个非空白绝对 URL；都空则用与观看相同的默认底座 https://qklive-testcos.cjs99.com/viewer/avatar/default-avatar.png（ZHIBO_DEFAULT_AVATAR_URL / zhibo.default-avatar-url）。本接口不做「CDN 域名 + 相对 objectKey」拼接。统一去掉 #fragment 与已有 v 参数后追加 ?v=z{员工id}（已有其它 query 则 &v=；身份与 email/account 本地部分一致）。响应始终非空并参与签名；前端 new VhallSDK 原样传 head（D111）。',
        ].join('\n'),
        operationId: 'getConsoleSdkSign',
        security: bearer,
        parameters: [
          {
            name: 'id',
            in: 'query',
            required: true,
            schema: { $ref: '#/components/schemas/BusinessUuid' },
            description: '本站活动 UUID',
          },
          {
            name: 'nickname',
            in: 'query',
            schema: { type: 'string', maxLength: 30 },
            description:
              '可选覆盖展示昵称。中控台页默认省略；省略则按本场 activity_member.nickname → 员工姓名 → 助理',
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseConsoleSdkSign'),
      },
    },
    '/api/v1/activities/console/question-history/{id}': {
      get: {
        tags: ['直播中控台'],
        summary: '中控台问答历史兜底',
        description:
          '讨论区提问历史：前端先微吼 vhall_get_live_history_question_msg；方法缺失 / 失败 / 无有效条再调本接口。' +
          '读本站 room_comment（comment_type=提问、未软删），按 comment_time 旧→新，最多 500 条。' +
          '响应形状对齐观看 /watch/question-history；admin JWT；无售卖门禁。活动不存在或已软删 → 400。',
        operationId: 'getConsoleQuestionHistory',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseConsoleQuestionHistory'),
      },
    },
    '/api/v1/activities/console/coupons/send/{id}': {
      post: {
        tags: ['直播中控台'],
        summary: '中控台发送优惠券',
        description:
          '中控台优惠券 Tab 二次确认后调用。同事务 upsert activity_coupon（一场一券一行，含 last_sender_dept_*）并 insert room_comment（comment_type=3，content 为券 JSON 快照含 employeeId 与岗位表部门 deptId/deptName，viewer_id 空，nickname 为当前员工昵称）。' +
          '发送人部门仅走 hr_uimsyhgwxx.yhid→bmid + sys_uimsbmxx，禁止 qw_bmxx；无岗位则部门字段省略。' +
          '前端仅当本接口成功后再调微吼 chat.sendCustomEvent({ type: coupon, data })。允许同一券反复发送。admin JWT。活动不存在或已软删 → 400。',
        operationId: 'sendConsoleCoupon',
        security: bearer,
        parameters: [activityId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/ConsoleSendCouponRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseConsoleSendCoupon'),
      },
    },
    '/api/v1/activities/console/coupon-history/{id}': {
      get: {
        tags: ['直播中控台'],
        summary: '中控台优惠券聊天历史',
        description:
          '读本站 room_comment（comment_type=3、未软删），按 comment_time 旧→新，最多 500 条，与微吼聊天按时间合并展示券卡片。' +
          '响应形状对齐观看 /watch/coupon-history；admin JWT；无售卖门禁。活动不存在或已软删 → 400。',
        operationId: 'getConsoleCouponHistory',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseWatchCouponHistory'),
      },
    },
    '/api/v1/live-reports/delete/{id}': {
      delete: {
        tags: ['直播报备'],
        summary: '软删报备',
        operationId: 'deleteLiveReport',
        security: bearer,
        parameters: [liveReportId],
        responses: ok200('#/components/schemas/ApiResponseId'),
      },
    },
    '/api/v1/viewers/list': {
      get: {
        tags: ['用户管理'],
        summary: '顶栏用户列表',
        operationId: 'listViewers',
        security: bearer,
        parameters: [
          ...pageParams,
          { name: 'keywordType', in: 'query', schema: { type: 'string', enum: ['nickname', 'id', 'phone'], description: 'nickname 昵称 / id 用户ID / phone 手机号' } },
          { name: 'keyword', in: 'query', schema: { type: 'string' } },
          { name: 'tagIds', in: 'query', schema: { type: 'string' } },
          { name: 'tagMatch', in: 'query', schema: { type: 'string', enum: ['any', 'none', 'all'], description: 'any 满足其一 / none 不包含 / all 全部满足' } },
          { name: 'sort', in: 'query', schema: { type: 'string' } },
          { name: 'order', in: 'query', schema: { type: 'string', enum: ['asc', 'desc'], description: 'asc 升序 / desc 降序' } },
        ],
        responses: ok200('#/components/schemas/ApiResponseViewerList'),
      },
    },
    '/api/v1/viewers/update-blacklist/{id}': {
      put: {
        tags: ['用户管理'],
        summary: '更新黑名单三开关',
        operationId: 'updateViewerBlacklist',
        security: bearer,
        parameters: [
          {
            name: 'id',
            in: 'path',
            required: true,
            schema: { type: 'integer', format: 'int64' },
            description: 'viewer.id',
          },
        ],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/ViewerBlacklistUpdateRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseViewerBlacklist'),
      },
    },
    '/api/v1/viewers/update-tags/{id}': {
      put: {
        tags: ['用户管理'],
        summary: '贴手动标签',
        operationId: 'updateViewerTags',
        security: bearer,
        parameters: [
          {
            name: 'id',
            in: 'path',
            required: true,
            schema: { type: 'integer', format: 'int64' },
            description: 'viewer.id',
          },
        ],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/ViewerTagsUpdateRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseViewerTags'),
      },
    },
    '/api/v1/viewers/learning-stats/summary': {
      get: {
        tags: ['用户管理'],
        summary: '学习统计汇总',
        operationId: 'viewerLearnStatsSummary',
        security: bearer,
        parameters: [...pageParams, { name: 'keywordType', in: 'query', schema: { type: 'string' } }, { name: 'keyword', in: 'query', schema: { type: 'string' } }],
        responses: ok200('#/components/schemas/ApiResponseViewerLearnSummary'),
      },
    },
    '/api/v1/viewers/learning-stats/courses': {
      get: {
        tags: ['用户管理'],
        summary: '学习统计课程数据（小节维度）',
        description:
          '管理端员工 JWT。一行=一名观众×一小节进度，读 course_lesson_progress，不是课级聚合。' +
          '筛选 keywordType/keyword/tagIds/tagMatch 同学习统计汇总；有筛时先收窄观众再取进度行。' +
          '默认按进度行 id 倒序。lessonName 为小节标题；lessonType 为内容类型中文；' +
          'lessonDurationSec 仅视频素材 meta（mm:ss 或 h:mm:ss）可解析时有秒数，否则 0。',
        operationId: 'viewerLearnStatsCourses',
        security: bearer,
        parameters: [
          ...pageParams,
          { name: 'keywordType', in: 'query', schema: { type: 'string', enum: ['nickname', 'id', 'phone'], default: 'nickname', description: 'nickname 昵称 / id 用户ID / phone 手机号' }, description: '用户关键字类型；缺省 nickname' },
          { name: 'keyword', in: 'query', schema: { type: 'string' }, description: '用户关键字；空则不过滤' },
          { name: 'tagIds', in: 'query', schema: { type: 'string' }, description: '标签 id，逗号分隔，如 1,2,3' },
          { name: 'tagMatch', in: 'query', schema: { type: 'string', enum: ['any', 'all', 'none'], default: 'any', description: 'any 满足其一 / none 不包含 / all 全部满足' }, description: '标签匹配；缺省 any' },
        ],
        responses: ok200('#/components/schemas/ApiResponseViewerLearnCourses'),
      },
    },
    '/api/v1/viewer-tags/list': {
      get: {
        tags: ['用户管理'],
        summary: '标签分页列表',
        operationId: 'listViewerTags',
        security: bearer,
        parameters: [
          ...pageParams,
          { name: 'type', in: 'query', schema: { type: 'integer', enum: [1, 2], description: '1 手动 / 2 自动' } },
          { name: 'name', in: 'query', schema: { type: 'string' } },
          { name: 'groupId', in: 'query', schema: { type: 'integer', format: 'int64' } },
        ],
        responses: ok200('#/components/schemas/ApiResponseViewerTagList'),
      },
    },
    '/api/v1/viewer-tags/detail/{id}': {
      get: {
        tags: ['用户管理'],
        summary: '标签详情',
        operationId: 'getViewerTagDetail',
        security: bearer,
        parameters: [
          { name: 'id', in: 'path', required: true, schema: { type: 'integer', format: 'int64' } },
        ],
        responses: ok200('#/components/schemas/ApiResponseViewerTagDetail'),
      },
    },
    '/api/v1/viewer-tags/create': {
      post: {
        tags: ['用户管理'],
        summary: '创建手动标签',
        operationId: 'createViewerTag',
        security: bearer,
        requestBody: {
          required: true,
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/ViewerTagCreateRequest' } },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseViewerTagDetail'),
      },
    },
    '/api/v1/viewer-tags/update/{id}': {
      put: {
        tags: ['用户管理'],
        summary: '更新标签',
        operationId: 'updateViewerTag',
        security: bearer,
        parameters: [
          { name: 'id', in: 'path', required: true, schema: { type: 'integer', format: 'int64' } },
        ],
        requestBody: {
          required: true,
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/ViewerTagUpdateRequest' } },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseViewerTagDetail'),
      },
    },
    '/api/v1/viewer-tags/delete/{id}': {
      delete: {
        tags: ['用户管理'],
        summary: '软删标签',
        description:
          '软删标签（写 deleted_time 等），并物理删除该标签下全部 viewer_tag_member。成功 data.id 为标签 BIGINT 主键，不是业务 UUID。',
        operationId: 'deleteViewerTag',
        security: bearer,
        parameters: [
          {
            name: 'id',
            in: 'path',
            required: true,
            schema: { type: 'integer', format: 'int64' },
            description: '标签主键（viewer_tag.id，BIGINT）',
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseIntId'),
      },
    },
    '/api/v1/viewer-tags/groups/list': {
      get: {
        tags: ['用户管理'],
        summary: '标签分组下拉',
        operationId: 'listViewerTagGroups',
        security: bearer,
        responses: ok200('#/components/schemas/ApiResponseViewerTagGroups'),
      },
    },
    '/api/v1/viewer-tags/groups/page': {
      get: {
        tags: ['用户管理'],
        summary: '标签分组分页',
        operationId: 'pageViewerTagGroups',
        security: bearer,
        parameters: [...pageParams, { name: 'name', in: 'query', schema: { type: 'string' } }],
        responses: ok200('#/components/schemas/ApiResponseViewerTagGroupPage'),
      },
    },
    '/api/v1/viewer-tags/groups/create': {
      post: {
        tags: ['用户管理'],
        summary: '创建标签分组',
        operationId: 'createViewerTagGroup',
        security: bearer,
        requestBody: {
          required: true,
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/ViewerTagGroupCreateRequest' } },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseViewerTagGroupItem'),
      },
    },
    '/api/v1/viewer-tags/groups/update/{id}': {
      put: {
        tags: ['用户管理'],
        summary: '更新标签分组',
        operationId: 'updateViewerTagGroup',
        security: bearer,
        parameters: [
          { name: 'id', in: 'path', required: true, schema: { type: 'integer', format: 'int64' } },
        ],
        requestBody: {
          required: true,
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/ViewerTagGroupUpdateRequest' } },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseViewerTagGroupItem'),
      },
    },
    '/api/v1/viewer-tags/groups/delete/{id}': {
      delete: {
        tags: ['用户管理'],
        summary: '软删标签分组',
        description:
          '软删标签分组。默认分组不可删；组内仍有未软删标签 → 400。成功 data.id 为分组 BIGINT 主键，不是业务 UUID。',
        operationId: 'deleteViewerTagGroup',
        security: bearer,
        parameters: [
          {
            name: 'id',
            in: 'path',
            required: true,
            schema: { type: 'integer', format: 'int64' },
            description: '标签分组主键（viewer_tag_group.id，BIGINT）',
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseIntId'),
      },
    },
    '/api/v1/outside/courses/list': {
      post: {
        tags: ['对外课程'],
        summary: '对外课程列表',
        description: [
          '当前不鉴权；不要 X-Outside-Token。',
          '只返回 shelfStatus=on 且 riskStatus=passed、未软删的课程。',
          '待审核、驳回、待上架、已下架、强制封禁都不返回。',
          'Body 可空 {} 或不传，等价于第 1 页全部已上架课程。',
          '默认按 listedTime 倒序。旧版 GET + keyword 已废止。',
        ].join(''),
        operationId: 'listOutsideCourses',
        security: [],
        requestBody: {
          required: false,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/OutsideCourseListRequest',
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseOutsideCourseList'),
      },
    },
  }

  finalizeSchemas(schemas)

  return {
    openapi: '3.0.3',
    info: {
      title: 'zhibo admin-service',
      description: [
        '钱坤直播管理端 API（admin-service :8081）。',
        '',
        '契约来源：`zhibo-shared/docs/API约定.md`（D58 中文注释/枚举；D61 落库类型与长度）。',
        '本文件供 Apifox 导入，也是正式后端按文档反向实现接口/建表校验的权威输入之一。',
        '',
        '**通则**',
        '- 成功：`code === 0`（HTTP 仍多为 200）',
        '- 失败：业务 `code` —— `400` 参数、`401` 未登录、`403` 停用、`500` 服务器、`501` 服务商未接入、云侧失败透传或 `502`',
        '- 活动路径 `{id}` 均为本站 **32 位业务 UUID（D74）**',
        '- 时间：`yyyy-MM-dd HH:mm:ss`',
        '- JSON 不输出 `null` 字段',
        '- 除登录白名单与 `/api/v1/outside/**` 外需 `Authorization: Bearer <accessToken>`',
      ].join('\n'),
      version: '2.8.0',
      contact: { name: 'zhibo-backend' },
    },
    servers: [
      { url: 'http://127.0.0.1:8081', description: '本地 admin-service' },
    ],
    tags: [
      { name: '登录鉴权' },
      { name: '图片上传' },
      { name: '素材中心' },
      { name: '活动管理' },
      { name: '直播报备' },
      { name: '课程' },
      { name: '对外课程' },
      { name: '用户管理' },
      { name: '统计' },
      { name: '中控台' },
      { name: '员工组织' },
    ],
    paths,
    components: {
      securitySchemes: {
        BearerAuth: {
          type: 'http',
          scheme: 'bearer',
          bearerFormat: 'JWT',
          description: '管理端 access JWT；不要带 refresh',
        },
      },
      parameters: {
        ActivityId: {
          name: 'id',
          in: 'path',
          required: true,
          schema: {
            ...BUSINESS_UUID,
            example: 'a1b2c3d4e5f67890abcdef1234567890',
          },
          description: '本站活动 id（D74）',
        },
        MaterialId: {
          name: 'id',
          in: 'path',
          required: true,
          schema: { ...BUSINESS_UUID },
          description: '素材 / 分组 id（D74）',
        },
        CourseId: {
          name: 'id',
          in: 'path',
          required: true,
          schema: { ...BUSINESS_UUID },
          description: '本站课程 id（D74）',
        },
        CourseIdPath: {
          name: 'courseId',
          in: 'path',
          required: true,
          schema: { ...BUSINESS_UUID },
          description: '本站课程 id（D74）',
        },
        LiveReportId: {
          name: 'id',
          in: 'path',
          required: true,
          schema: { ...BUSINESS_UUID },
          description: '直播报备 id（D74）',
        },
      },
      schemas,
    },
    security: bearer,
  }
}

function buildLive() {
  const schemas = {
    BusinessUuid: { ...BUSINESS_UUID },
    ApiResponseBase: {
      type: 'object',
      required: ['code', 'msg'],
      properties: {
        code: { type: 'integer', example: 0 },
        msg: { type: 'string', example: 'ok' },
      },
    },
    Provider: {
      type: 'string',
      enum: ['vhall', 'aliyun', 'tencent'],
      description: 'vhall 微吼 / aliyun 阿里云 / tencent 腾讯云。创建后不可改',
    },
    ActivityType: {
      type: 'integer',
      enum: [1, 2, 3, 4],
      description: '1 音频 / 2 视频 / 3 互动 / 4 录播。创建后不可改',
    },
    ActivityState: {
      type: 'integer',
      enum: [0, 1, 2, 3, 4, 5],
      description: '0 全部(列表筛) / 1 直播 / 2 预告 / 3 结束 / 4 点播 / 5 回放',
    },
    IdData: {
      type: 'object',
      required: ['id'],
      properties: { id: { $ref: '#/components/schemas/BusinessUuid' } },
    },
    WatchActivitySummary: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        title: { type: 'string' },
        state: { $ref: '#/components/schemas/ActivityState' },
        startTime: { type: 'string' },
        coverUrl: { type: 'string' },
      },
    },
    WatchActivityListData: {
      type: 'object',
      properties: {
        total: { type: 'integer', format: 'int64' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/WatchActivitySummary' },
        },
      },
    },
    ViewerLearningCourseItem: {
      type: 'object',
      required: ['id', 'title', 'subCourseCount', 'lessonCount'],
      properties: {
        id: {
          $ref: '#/components/schemas/BusinessUuid',
          description: '本站课程 UUID',
        },
        title: { type: 'string', description: '课程名称' },
        coverUrl: { type: 'string', description: '封面 URL' },
        subCourseCount: {
          type: 'integer',
          description: '未软删子课程数；库：INT',
        },
        lessonCount: {
          type: 'integer',
          description: '小节数（course.content_count）；库：INT',
        },
        acquiredTime: {
          type: 'string',
          description:
            '本学员获得权益时间（course_user.created_time），yyyy-MM-dd HH:mm:ss',
        },
      },
    },
    ViewerLearningCourseListData: {
      type: 'object',
      required: ['total', 'list'],
      properties: {
        total: { type: 'integer', format: 'int64', description: '总条数' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/ViewerLearningCourseItem' },
          description: '课程列表',
        },
      },
    },
    ViewerLearningSubCourseItem: {
      type: 'object',
      required: ['id', 'title', 'lessonCount'],
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: '子课程节点 id；库：BIGINT',
        },
        title: { type: 'string', description: '子课程标题' },
        coverUrl: { type: 'string', description: '子课程封面 URL' },
        lessonCount: {
          type: 'integer',
          description: '该子课程下小节数（含章内）；库：INT',
        },
      },
    },
    ViewerLearningCatalogNode: {
      type: 'object',
      required: ['id', 'parentId', 'nodeType', 'title'],
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: '目录节点 id；库：BIGINT',
        },
        parentId: {
          type: 'integer',
          format: 'int64',
          description: '父节点 id；根为 0；库：BIGINT',
        },
        nodeType: {
          type: 'integer',
          description: '1 章节 / 2 子课程 / 3 小节',
          enum: [1, 2, 3],
        },
        title: { type: 'string', description: '节点标题' },
        contentKind: {
          type: 'integer',
          nullable: true,
          description: '仅小节：1 直播 / 2 图片 / 3 视频 / 4 文档 / 5 文章',
          enum: [1, 2, 3, 4, 5],
        },
        contentId: {
          type: 'string',
          nullable: true,
          description: '小节内容 id（直播 UUID 或素材 UUID）',
        },
        coverUrl: { type: 'string', nullable: true, description: '封面 URL' },
        lessonCount: {
          type: 'integer',
          nullable: true,
          description: '章节：直属小节数；子课程：其下全部小节数；小节省略',
        },
        contentKindLabel: {
          type: 'string',
          nullable: true,
          description: '小节类型中文：直播 / 图片 / 视频 / 文档 / 文章',
        },
        metaLabel: {
          type: 'string',
          nullable: true,
          description: '直播「支持回放」等副文案；无则省略',
        },
        participateStatus: {
          type: 'integer',
          nullable: true,
          description: '0 未开始 / 1 进行中 / 2 已完成；仅小节本学员',
          enum: [0, 1, 2],
        },
        progressPercent: {
          type: 'integer',
          nullable: true,
          description: '仅小节：本学员进度 0～100',
        },
      },
    },
    ViewerLearningLessonContentData: {
      type: 'object',
      required: ['lessonNodeId', 'title', 'contentKind', 'contentId', 'participateStatus'],
      properties: {
        lessonNodeId: {
          type: 'integer',
          format: 'int64',
          description: '小节节点 id；库：BIGINT',
        },
        title: { type: 'string', description: '小节标题' },
        contentKind: {
          type: 'integer',
          description: '1 直播 / 2 图片 / 3 视频 / 4 文档 / 5 文章',
          enum: [1, 2, 3, 4, 5],
        },
        contentId: { type: 'string', description: '直播 UUID 或素材 UUID' },
        participateStatus: {
          type: 'integer',
          description: '0 未开始 / 1 进行中 / 2 已完成',
          enum: [0, 1, 2],
        },
        progressPercent: { type: 'integer', description: '0～100' },
        progressBarSec: {
          type: 'integer',
          nullable: true,
          description: '进度条最远点（秒）；图/文/档可空',
        },
        materialUrl: { type: 'string', nullable: true, description: '素材 URL' },
        mimeType: { type: 'string', nullable: true, description: 'MIME 类型' },
        materialName: {
          type: 'string',
          nullable: true,
          description: '素材名称',
        },
        articleHtml: { type: 'string', nullable: true, description: '仅文章正文 HTML' },
        author: {
          type: 'string',
          nullable: true,
          description: '仅文章作者；库：material_article.author VARCHAR(16)',
        },
        activityId: {
          type: 'string',
          nullable: true,
          description: '仅直播：活动 UUID',
        },
      },
    },
    ViewerLearningCourseDetailData: {
      type: 'object',
      required: ['id', 'title', 'subCourses', 'nodes'],
      properties: {
        id: {
          $ref: '#/components/schemas/BusinessUuid',
          description: '本站课程 UUID',
        },
        title: { type: 'string', description: '课程名称' },
        coverUrl: { type: 'string', description: '封面 URL' },
        introduction: { type: 'string', description: '一句话简介' },
        detailHtml: { type: 'string', description: '详情富文本 HTML' },
        validityLabel: { type: 'string' },
        subCourses: {
          type: 'array',
          items: { $ref: '#/components/schemas/ViewerLearningSubCourseItem' },
          description: '根下子课程卡片（node_type=2 且 parentId=0）',
        },
        nodes: {
          type: 'array',
          items: { $ref: '#/components/schemas/ViewerLearningCatalogNode' },
          description: '全部未软删目录节点（扁平；同父排序与管理端一致）',
        },
      },
    },
    WatchEmbedData: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        title: { type: 'string' },
        state: { $ref: '#/components/schemas/ActivityState' },
        type: { $ref: '#/components/schemas/ActivityType' },
        nickname: { type: 'string' },
        guestId: { type: 'string' },
        startTime: { type: 'string' },
        endTime: { type: 'string' },
        embedUrl: { type: 'string', format: 'uri' },
      },
    },
    VhallSdkPayload: {
      type: 'object',
      description: '微吼签名/init 鉴权字段；脚本 CDN 由前端适配器加载（D93）',
      properties: {
        account: {
          type: 'string',
          description:
            '第三方用户唯一标识（观众=数字 viewerId）。本站回显；微吼 init 以 email 为准、勿与 email 同时传冲突字段。聊天回包未必带回，认人不能只靠它（D111）',
        },
        email: { type: 'string', description: '{viewerId}@qkzb.com，参与签名；new VhallSDK 必须用这份' },
        username: { type: 'string', description: '优先 Query nickname；空则默认「观众」' },
        head: {
          type: 'string',
          description:
            '对应微吼 JSSDK 初始化参数 head：服务端下发已拼好的绝对 http(s) URL，不是相对路径；本签名接口不会把 objectKey 与 CDN 域名再拼接。底座来源：观看端取 viewer.avatar_url（去空白）；中控台按本场 activity_member.avatar_url → 员工 headimgurl → user_avatar。空白或无记录时改用默认底座 https://qklive-testcos.cjs99.com/viewer/avatar/default-avatar.png（可用环境变量 ZHIBO_DEFAULT_AVATAR_URL / 配置 zhibo.default-avatar-url 覆盖）。拼接规则：去掉 #fragment、去掉已有查询参数 v（大小写不敏感，其它 query 保留），再追加 ?v={身份}（URL 已有其它 query 则用 &v=）。身份与进房 email 本地部分 / sdk.account 一致：观看为数字 viewerId（例 ?v=12），中控台为 z{员工id}（例 ?v=z276）。响应始终非空字符串；非空 head 参与签名（空串勿签入，否则微吼 20005）。前端 new VhallSDK 须原样传入本字段，不要自行改写或再拼 CDN（D111）',
        },
        webinarId: { type: 'string', description: '微吼活动号（= provider_room_id）；仅 provider=vhall' },
        appKey: { type: 'string', description: '微吼 app_key' },
        signedAt: { type: 'string', description: '签名时间戳（约 5 分钟有效）' },
        signType: { type: 'integer', example: 0, description: '签名类型；通常 0' },
        sign: { type: 'string', description: '服务端 SecretKey 算出的签名；勿返回 secret' },
      },
    },
    WatchSummaryData: {
      type: 'object',
      description:
        '观看页活动摘要 / 页面壳（D95）：标题、态、时间、回放开关与有效期；全部取本库 Activity，不含 SDK 签名',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        provider: { $ref: '#/components/schemas/Provider' },
        title: { type: 'string' },
        state: { $ref: '#/components/schemas/ActivityState' },
        type: { $ref: '#/components/schemas/ActivityType' },
        startTime: { type: 'string' },
        endTime: { type: 'string' },
        playbackEnabled: { type: 'boolean' },
        playbackValidityType: { $ref: '#/components/schemas/PlaybackValidityType' },
        playbackValidityDays: { type: 'integer' },
        playbackExpired: { type: 'boolean' },
        playbackSpeedAllow: { type: 'boolean' },
        playbackFastForwardAllow: { type: 'boolean' },
      },
    },
    WatchSdkData: {
      type: 'object',
      description:
        '观看 SDK 签名载荷（D92/D93/D94/D95/D96）：仅 id / provider / viewerId / nickname / sdk；无 guestId / scriptUrl / jqueryUrl / embedUrl / title / state / playback*；响应 viewerId 来自观众 JWT',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        provider: { $ref: '#/components/schemas/Provider' },
        nickname: { type: 'string' },
        viewerId: { type: 'integer', format: 'int64' },
        sdk: { $ref: '#/components/schemas/VhallSdkPayload' },
      },
    },
    WatchAccessData: {
      type: 'object',
      description: '观看售卖门禁状态（D90/D115）。任意场次须先登录；加密再密码；指定用户再权益。',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        sellAlone: { type: 'boolean' },
        sellType: { $ref: '#/components/schemas/SellType' },
        accessState: {
          type: 'string',
          enum: ['ok', 'need_password', 'need_login', 'forbidden', 'expired'],
          description:
            'ok 已放行 / need_password 已登录但缺观看密码 / need_login 需登录（含免费场） / forbidden 无观看权限 / expired 访问已过期',
        },
        accessExpired: { type: 'boolean' },
        accessValidityType: { $ref: '#/components/schemas/AccessValidityType' },
        accessValidityDays: { type: 'integer' },
      },
    },
    WatchAccessVerifyPasswordRequest: {
      type: 'object',
      required: ['password'],
      properties: {
        password: {
          type: 'string',
          pattern: '^\\d{6}$',
          description: '明文 6 位数字观看密码',
        },
      },
    },
    WatchAccessVerifyPasswordData: {
      type: 'object',
      properties: {
        accessToken: { type: 'string' },
        expiresIn: { type: 'integer', format: 'int64' },
      },
    },
    WatchChatHistoryItem: {
      type: 'object',
      properties: {
        senderId: { type: 'string' },
        senderName: { type: 'string' },
        msg: { type: 'string' },
        msgId: { type: 'string' },
        avatar: { type: 'string' },
        roleName: { type: 'integer' },
        timestamp: { type: 'integer', format: 'int64' },
      },
    },
    WatchChatHistoryData: {
      type: 'object',
      description: '回放页只读聊天历史（D91）',
      properties: {
        total: { type: 'integer', format: 'int64' },
        page: { type: 'integer' },
        pageSize: { type: 'integer' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/WatchChatHistoryItem' },
        },
      },
    },
    WatchQuestionHistoryItem: {
      type: 'object',
      properties: {
        questionId: { type: 'string' },
        senderId: { type: 'string' },
        senderName: { type: 'string' },
        msg: { type: 'string' },
        msgId: { type: 'string' },
        roleName: { type: 'integer' },
        timestamp: { type: 'integer', format: 'int64' },
        answerContent: { type: 'string' },
        answerNickname: { type: 'string' },
        answerRoleName: { type: 'integer' },
      },
    },
    WatchQuestionHistoryData: {
      type: 'object',
      description:
        '本站 room_comment 提问行（D91）。回放页直接用；直播页微吼问答历史空/失败后兜底。',
      properties: {
        total: { type: 'integer', format: 'int64' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/WatchQuestionHistoryItem' },
        },
      },
    },
    ConsoleQuestionHistoryData: {
      type: 'object',
      description:
        '中控台问答历史兜底。读本站 room_comment 提问行；形状对齐 WatchQuestionHistoryData；admin JWT。',
      properties: {
        total: { type: 'integer', format: 'int64' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/WatchQuestionHistoryItem' },
        },
      },
    },
    ConsoleSendCouponRequest: {
      type: 'object',
      required: [
        'couponId',
        'couponNo',
        'couponName',
        'couponAmount',
        'couponStartDate',
        'couponEndDate',
      ],
      description:
        '中控台发券 Body。字段来自订单中心券池列表；couponId≤0 或必填串空白 → 400。',
      properties: {
        couponId: {
          type: 'integer',
          format: 'int64',
          description: '订单中心券池主键 id；须为正整数',
        },
        couponNo: { type: 'string', maxLength: 64, description: '券编号，如 CPN2401' },
        couponName: { type: 'string', maxLength: 256, description: '券名称' },
        couponAmount: {
          type: 'string',
          maxLength: 32,
          description: '金额字符串，如 500.00；前端展示时加人民币符号',
        },
        couponStartDate: { type: 'string', maxLength: 32, description: '有效期起' },
        couponEndDate: { type: 'string', maxLength: 32, description: '有效期止' },
        allSku: {
          type: 'boolean',
          description: 'true 表示全部商品；省略按 false',
        },
        deptNames: {
          type: 'array',
          items: { type: 'string' },
          description: '可用事业部名称列表；服务端顿号拼接写入关系表',
        },
        skuNames: {
          type: 'array',
          items: { type: 'string' },
          description: '可用商品名称列表；allSku=true 时可空',
        },
      },
    },
    ConsoleSendCouponResponse: {
      type: 'object',
      description: '发券成功后返回；前端用其组 sendCustomEvent data 并本地上屏',
      properties: {
        commentId: { type: 'integer', format: 'int64', description: 'room_comment.id' },
        couponId: { type: 'integer', format: 'int64' },
        couponNo: { type: 'string' },
        couponName: { type: 'string' },
        couponAmount: { type: 'string' },
        couponStartDate: { type: 'string' },
        couponEndDate: { type: 'string' },
        employeeId: { type: 'integer', format: 'int64', description: '发送员工 id' },
        nickname: { type: 'string', description: '发送昵称快照' },
        deptId: {
          type: 'integer',
          format: 'int64',
          description:
            '发送人部门 id（岗位表 hr_uimsyhgwxx.bmid → sys_uimsbmxx.rs_mainkey_id）；无在职岗位则省略。禁止读 qw_bmxx',
        },
        deptName: {
          type: 'string',
          description: '发送人部门名（sys_uimsbmxx.bmmc）；与 deptId 同有同无',
        },
        commentTime: {
          type: 'string',
          description: 'comment_time，格式 yyyy-MM-dd HH:mm:ss',
        },
      },
    },
    WatchCouponItem: {
      type: 'object',
      description: '本场已发券一行（activity_coupon 快照）',
      properties: {
        couponId: { type: 'integer', format: 'int64' },
        couponNo: { type: 'string' },
        couponName: { type: 'string' },
        couponAmount: { type: 'string' },
        couponStartDate: { type: 'string' },
        couponEndDate: { type: 'string' },
        allSku: { type: 'boolean' },
        deptNames: {
          type: 'string',
          description: '券可用事业部顿号拼接；可空串（不是发送人部门）',
        },
        skuNames: { type: 'string', description: '顿号拼接；可空串' },
        lastSentTime: {
          type: 'string',
          description: '最近发送时间 yyyy-MM-dd HH:mm:ss',
        },
        lastSenderEmployeeId: {
          type: 'integer',
          format: 'int64',
          description: '最近发送员工 id；侧栏领取传订单中心',
        },
        lastSenderNickname: { type: 'string', description: '最近发送昵称' },
        lastSenderDeptId: {
          type: 'integer',
          format: 'int64',
          description:
            '最近发送人部门 id（岗位表解析）；无岗位则省略',
        },
        lastSenderDeptName: {
          type: 'string',
          description: '最近发送人部门名；与 lastSenderDeptId 同有同无',
        },
      },
    },
    WatchCouponListData: {
      type: 'object',
      properties: {
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/WatchCouponItem' },
          description: '按 last_sent_time 新→旧',
        },
      },
    },
    WatchCouponHistoryItem: {
      type: 'object',
      description: '优惠券聊天历史单条（room_comment type=3）',
      properties: {
        id: { type: 'integer', format: 'int64', description: 'room_comment.id' },
        senderName: { type: 'string' },
        timestamp: {
          type: 'integer',
          format: 'int64',
          description: 'comment_time 毫秒（上海）',
        },
        couponId: { type: 'integer', format: 'int64' },
        couponNo: { type: 'string' },
        couponName: { type: 'string' },
        couponAmount: { type: 'string' },
        couponStartDate: { type: 'string' },
        couponEndDate: { type: 'string' },
        employeeId: { type: 'integer', format: 'int64' },
        deptId: {
          type: 'integer',
          format: 'int64',
          description:
            '发送人部门 id（content JSON；岗位表链路）；无则省略',
        },
        deptName: {
          type: 'string',
          description: '发送人部门名；与 deptId 同有同无',
        },
      },
    },
    WatchCouponHistoryData: {
      type: 'object',
      properties: {
        total: { type: 'integer', format: 'int64', description: '截断前条数' },
        list: {
          type: 'array',
          items: { $ref: '#/components/schemas/WatchCouponHistoryItem' },
          description: '旧→新；最多 500 条',
        },
      },
    },
    ChatFilterData: {
      type: 'object',
      required: ['pageUrl'],
      properties: {
        pageUrl: { type: 'string', format: 'uri' },
      },
    },

    ViewerUserInfo: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        hasPhone: { type: 'boolean' },
        phoneMasked: { type: 'string' },
        nickname: { type: 'string' },
        avatarUrl: { type: 'string' },
        avatarObjectKey: { type: 'string' },
      },
    },
    ViewerTokenPairData: {
      type: 'object',
      properties: {
        accessToken: { type: 'string' },
        expiresIn: { type: 'integer' },
        refreshToken: { type: 'string' },
        refreshExpiresIn: { type: 'integer' },
        user: { $ref: '#/components/schemas/ViewerUserInfo' },
      },
    },
    ViewerAuthWechatLoginRequest: {
      type: 'object',
      required: ['code', 'agreed'],
      properties: {
        code: { type: 'string' },
        agreed: { type: 'boolean', enum: [true], description: 'true 已勾选同意' },
      },
    },
    ViewerAuthPhoneLoginRequest: {
      type: 'object',
      required: ['loginCode', 'phoneCode', 'agreed'],
      properties: {
        loginCode: { type: 'string' },
        phoneCode: { type: 'string' },
        agreed: { type: 'boolean', enum: [true], description: 'true 已勾选同意' },
      },
    },
    ViewerAuthRefreshRequest: {
      type: 'object',
      required: ['accessToken', 'refreshToken'],
      properties: {
        accessToken: { type: 'string' },
        refreshToken: { type: 'string' },
      },
    },
    ViewerProfileUpdateRequest: {
      type: 'object',
      properties: {
        nickname: { type: 'string', maxLength: 16 },
        avatarUrl: { type: 'string', maxLength: 512 },
        avatarObjectKey: { type: 'string', maxLength: 1024 },
      },
    },
    ViewerUploadFileData: {
      type: 'object',
      properties: {
        objectKey: { type: 'string' },
        url: { type: 'string' },
        location: { type: 'string' },
      },
    },
    ViewerTosStsData: {
      type: 'object',
      properties: {
        region: { type: 'string' },
        endpoint: { type: 'string' },
        bucketName: { type: 'string' },
        accessKeyId: { type: 'string' },
        secretAccessKey: { type: 'string' },
        sessionToken: { type: 'string' },
        expiredTime: { type: 'string' },
        cdnUrl: { type: 'string' },
      },
    },
    ViewerFeedbackImageRef: {
      type: 'object',
      required: ['url', 'objectKey'],
      properties: { url: { type: 'string' }, objectKey: { type: 'string' } },
    },
    ViewerFeedbackSubmitRequest: {
      type: 'object',
      required: ['category', 'content'],
      properties: {
        category: { type: 'integer', enum: [1, 2, 3, 4], description: '1 课程权益 / 2 视频播放 / 3 学习体验 / 4 其他' },
        content: { type: 'string', maxLength: 200 },
        contactPhone: { type: 'string' },
        images: {
          type: 'array',
          maxItems: 4,
          items: { $ref: '#/components/schemas/ViewerFeedbackImageRef' },
        },
      },
    },
    ViewerFeedbackListItem: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        category: { type: 'integer' },
        categoryLabel: { type: 'string' },
        content: { type: 'string' },
        imageCount: { type: 'integer' },
        createdTime: { type: 'string' },
      },
    },
    ViewerFeedbackListData: {
      type: 'object',
      properties: {
        total: { type: 'integer', format: 'int64' },
        page: { type: 'integer' },
        pageSize: { type: 'integer' },
        list: { type: 'array', items: { $ref: '#/components/schemas/ViewerFeedbackListItem' } },
      },
    },
    ViewerFeedbackDetailData: {
      allOf: [
        { $ref: '#/components/schemas/ViewerFeedbackListItem' },
        {
          type: 'object',
          properties: {
            contactPhone: { type: 'string' },
            images: {
              type: 'array',
              items: { $ref: '#/components/schemas/ViewerFeedbackImageRef' },
            },
          },
        },
      ],
    },
    ViewerPresencePingRequest: {
      type: 'object',
      properties: {
        scene: {
          type: 'string',
          enum: ['miniapp', 'live'],
          description: 'miniapp 小程序 / live 直播间',
        },
      },
    },
    ViewerPresencePingData: {
      type: 'object',
      properties: { lastVisitTime: { type: 'string' } },
    },
    WatchProductItem: {
      type: 'object',
      properties: {
        spuId: { type: 'string' },
        productName: { type: 'string' },
      },
    },
    WatchProductListData: {
      type: 'object',
      properties: {
        list: { type: 'array', items: { $ref: '#/components/schemas/WatchProductItem' } },
      },
    },
    WatchRecordPlayItem: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        title: { type: 'string' },
        duration: { type: 'string' },
        coverUrl: { type: 'string' },
        tosUrl: { type: 'string' },
        createdTime: { type: 'string' },
      },
    },
    WatchRecordPlayListData: {
      type: 'object',
      properties: {
        list: { type: 'array', items: { $ref: '#/components/schemas/WatchRecordPlayItem' } },
      },
    },
    WatchReplayStartRequest: {
      type: 'object',
      required: ['recordId'],
      properties: { recordId: { type: 'integer', format: 'int64' } },
    },
    WatchReplayStartData: {
      type: 'object',
      properties: { socketId: { type: 'string', maxLength: 32 } },
    },
    WatchReplayHeartbeatRequest: {
      type: 'object',
      required: ['socketId'],
      properties: { socketId: { type: 'string' } },
    },
    WatchReplayHeartbeatData: {
      type: 'object',
      properties: { lastReportTime: { type: 'string' } },
    },
    WatchCommentDeleteRequest: {
      type: 'object',
      properties: {
        msgId: { type: 'string' },
        commentId: { type: 'integer', format: 'int64' },
      },
    },
    WatchAudienceAttributeRequest: {
      type: 'object',
      required: ['inviterId'],
      description:
        '邀请归因（D97）。被邀请观众 id 只从 Authorization 解析，Body 不传 viewerId。inviterKind 区分员工/观众邀请；audience 当前不落库 → 400「不记录观众邀请」（D77）',
      properties: {
        inviterId: {
          type: 'integer',
          format: 'int64',
          description:
            '邀请人 id；employee 时为员工 rs_mainkey_id，audience 时为观众 viewer.id（当前不落库）；库：BIGINT',
        },
        inviterKind: {
          type: 'string',
          enum: ['employee', 'audience'],
          description: 'employee 员工邀请 / audience 观众邀请',
        },
      },
    },
    WatchAudienceAttributeData: {
      type: 'object',
      properties: {
        id: { type: 'integer', format: 'int64' },
        viewerId: { type: 'integer', format: 'int64' },
      },
    },
    WatchAudienceSalesmanData: {
      type: 'object',
      description:
        '本场业务员。读 activity_audience：优先 employee_inviter_id（员工邀请人），否则 importer_id（导入人）；无本场行或两字段皆空则 salesmanId 为 null。供观看页创建订单传订单中心 salesmanId。',
      properties: {
        salesmanId: {
          type: 'integer',
          format: 'int64',
          nullable: true,
          description:
            '员工 sys_useraccount.rs_mainkey_id。有有效邀请人用邀请人；否则用导入人；皆无则为 null（前端创建订单时不传 salesmanId）。',
        },
      },
    },
    WatchCourseEnrollRequest: {
      type: 'object',
      properties: {
        inviterId: {
          type: 'integer',
          format: 'int64',
          description:
            '选填；员工 rs_mainkey_id；有值且行上邀请仍空则写入；省略则只 upsert 学员行',
        },
      },
    },
    WatchCourseEnrollData: {
      type: 'object',
      properties: {
        id: {
          type: 'integer',
          format: 'int64',
          description: 'course_user 行 id',
        },
        viewerId: { type: 'integer', format: 'int64', description: '观众 id' },
        courseId: {
          $ref: '#/components/schemas/BusinessUuid',
          description: '课程 UUID',
        },
        created: {
          type: 'boolean',
          description: 'true 新建；false 已有行（不改权益/到期）',
        },
        title: { type: 'string', description: '课程标题' },
      },
    },
    IdDataInt: {
      type: 'object',
      required: ['id'],
      properties: { id: { type: 'integer', format: 'int64' } },
    },

    VhallPresenceCallback: {
      type: 'object',
      description: '微吼上下线回调 body（字段以微吼文档为准）',
      properties: {
        method_id: { type: 'integer', example: 20 },
        type: { type: 'string', enum: ['Join', 'Leave'] },
        webinar_id: { type: 'string' },
        sender_id: { type: 'string' },
        time: { type: 'integer', description: 'Unix 秒' },
        mid: { type: 'string' },
        signature: { type: 'string' },
        context: {
          type: 'object',
          properties: {
            nickname: { type: 'string' },
            avatar: { type: 'string' },
          },
        },
      },
    },
  }

  for (const [name, data] of Object.entries({
    ApiResponseId: { $ref: '#/components/schemas/IdData' },
    ApiResponseWatchList: { $ref: '#/components/schemas/WatchActivityListData' },
    ApiResponseViewerLearningCourses: {
      $ref: '#/components/schemas/ViewerLearningCourseListData',
    },
    ApiResponseViewerLearningCourseDetail: {
      $ref: '#/components/schemas/ViewerLearningCourseDetailData',
    },
    ApiResponseViewerLearningLessonContent: {
      $ref: '#/components/schemas/ViewerLearningLessonContentData',
    },
    ApiResponseWatchEmbed: { $ref: '#/components/schemas/WatchEmbedData' },
    ApiResponseWatchSummary: { $ref: '#/components/schemas/WatchSummaryData' },
    ApiResponseConsoleSummary: { $ref: '#/components/schemas/ConsoleSummaryData' },
    ApiResponseConsoleSdkSign: { $ref: '#/components/schemas/ConsoleSdkSignData' },
    ApiResponseConsoleQuestionHistory: {
      $ref: '#/components/schemas/ConsoleQuestionHistoryData',
    },
    ApiResponseConsoleSendCoupon: {
      $ref: '#/components/schemas/ConsoleSendCouponResponse',
    },
    ApiResponseWatchCouponHistory: {
      $ref: '#/components/schemas/WatchCouponHistoryData',
    },
    ApiResponseWatchCoupons: {
      $ref: '#/components/schemas/WatchCouponListData',
    },
    ConsoleSummaryData: {
      type: 'object',
      description:
        '中控台页壳（D110）：字段对齐观看 /watch/summary；本库 Activity；admin JWT；无观众售卖门禁',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        provider: { $ref: '#/components/schemas/Provider' },
        title: { type: 'string' },
        state: { $ref: '#/components/schemas/ActivityState' },
        type: { $ref: '#/components/schemas/ActivityType' },
        startTime: { type: 'string' },
        endTime: { type: 'string' },
        playbackEnabled: { type: 'boolean' },
        playbackValidityType: { $ref: '#/components/schemas/PlaybackValidityType' },
        playbackValidityDays: { type: 'integer' },
        playbackExpired: { type: 'boolean' },
        playbackSpeedAllow: { type: 'boolean' },
        playbackFastForwardAllow: { type: 'boolean' },
      },
    },
    ConsoleSdkSignData: {
      type: 'object',
      description:
        '中控台员工进房签名（D110）：对齐观看 sdk-sign；employeeId 代替 viewerId；sdk.email 为 z{员工id}@qkzb.com（角色仍用 e{id}）；昵称本场成员优先否则员工资料；sdk.head 为本场/员工绝对头像 URL（或默认底座）再拼 ?v=z{员工id}，不做 CDN+相对路径拼接，始终非空并入签',
      properties: {
        id: { $ref: '#/components/schemas/BusinessUuid' },
        provider: { $ref: '#/components/schemas/Provider' },
        nickname: {
          type: 'string',
          description: '与 sdk.username 同；本场成员昵称 → 员工姓名 → 助理',
        },
        employeeId: { type: 'integer', format: 'int64', description: '当前登录员工 rs_mainkey_id' },
        sdk: { $ref: '#/components/schemas/VhallSdkPayload' },
      },
    },
    ApiResponseWatchSdk: { $ref: '#/components/schemas/WatchSdkData' },
    ApiResponseWatchAccess: { $ref: '#/components/schemas/WatchAccessData' },
    ApiResponseWatchAccessVerifyPassword: {
      $ref: '#/components/schemas/WatchAccessVerifyPasswordData',
    },
    ApiResponseWatchChatHistory: { $ref: '#/components/schemas/WatchChatHistoryData' },
    ApiResponseWatchQuestionHistory: {
      $ref: '#/components/schemas/WatchQuestionHistoryData',
    },
    ApiResponseAttendeeList: { $ref: '#/components/schemas/AttendeeListData' },
    ApiResponsePresenceTrend: { $ref: '#/components/schemas/PresenceTrend' },
    ApiResponseChatFilter: { $ref: '#/components/schemas/ChatFilterData' },

    ApiResponseViewerTokenPair: { $ref: '#/components/schemas/ViewerTokenPairData' },
    ApiResponseViewerUserInfo: { $ref: '#/components/schemas/ViewerUserInfo' },
    ApiResponseViewerUploadFile: { $ref: '#/components/schemas/ViewerUploadFileData' },
    ApiResponseViewerTosSts: { $ref: '#/components/schemas/ViewerTosStsData' },
    ApiResponseViewerFeedbackId: { $ref: '#/components/schemas/IdDataInt' },
    ApiResponseViewerFeedbackList: { $ref: '#/components/schemas/ViewerFeedbackListData' },
    ApiResponseViewerFeedbackDetail: { $ref: '#/components/schemas/ViewerFeedbackDetailData' },
    ApiResponseViewerPresencePing: { $ref: '#/components/schemas/ViewerPresencePingData' },
    ApiResponseWatchProducts: { $ref: '#/components/schemas/WatchProductListData' },
    ApiResponseWatchPlayRecords: { $ref: '#/components/schemas/WatchRecordPlayListData' },
    ApiResponseWatchReplayStart: { $ref: '#/components/schemas/WatchReplayStartData' },
    ApiResponseWatchReplayHeartbeat: { $ref: '#/components/schemas/WatchReplayHeartbeatData' },
    ApiResponseWatchAudienceAttribute: { $ref: '#/components/schemas/WatchAudienceAttributeData' },
    ApiResponseWatchAudienceSalesman: { $ref: '#/components/schemas/WatchAudienceSalesmanData' },
    ApiResponseWatchCourseEnroll: { $ref: '#/components/schemas/WatchCourseEnrollData' },
    ApiResponseIntId: { $ref: '#/components/schemas/IdDataInt' },

  })) {
    schemas[name] = wrapData(data)
  }


  const watchId = { $ref: '#/components/parameters/WatchActivityId' }
  const guestParams = [
    {
      name: 'guestId',
      in: 'query',
      schema: { type: 'string', pattern: "^[A-Za-z0-9]+$" },
      description: '只能字母数字；不传则后端生成（仅 detail 嵌入）',
    },
    {
      name: 'nickname',
      in: 'query',
      schema: { type: 'string', maxLength: 50 },
      description: '默认「观众」',
    },
    {
      name: 'viewerId',
      in: 'query',
      schema: { type: 'integer', format: 'int64' },
      description: '观众数字 id；有则 email 为 {id}@qkzb.com',
    },
  ]
  const sdkSignParams = [
    {
      name: 'id',
      in: 'query',
      required: true,
      schema: {
        ...BUSINESS_UUID,
        example: 'a1b2c3d4e5f67890abcdef1234567890',
      },
      description: '本站活动 UUID（Query；相对 D62 的明确例外，见 D92）',
    },
    {
      name: 'nickname',
      in: 'query',
      schema: { type: 'string', maxLength: 50 },
      description: '默认「观众」；小程序已按展示昵称规则算好并拼进 web-view URL；viewerId 只从 Authorization 解析（D96）',
    },
  ]
  const activityAccessHeader = {
    name: 'X-Activity-Access',
    in: 'header',
    required: false,
    schema: { type: 'string' },
    description: '本场访问 JWT（加密售卖解锁后；勿塞进观众 Authorization）',
  }
  const viewerBearer = [{ BearerViewer: [] }]
  const paths = {
    '/api/v1/viewer/auth/wechat-login': {
      post: {
        tags: ['观众鉴权'],
        summary: '微信小程序登录',
        operationId: 'viewerWechatLogin',
        security: [],
        requestBody: {
          required: true,
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/ViewerAuthWechatLoginRequest' } },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseViewerTokenPair'),
      },
    },
    '/api/v1/viewer/auth/phone-login': {
      post: {
        tags: ['观众鉴权'],
        summary: '手机号授权登录',
        operationId: 'viewerPhoneLogin',
        security: [],
        requestBody: {
          required: true,
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/ViewerAuthPhoneLoginRequest' } },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseViewerTokenPair'),
      },
    },
    // 仅本地 profile：POST /api/v1/viewer/auth/dev-phone-login —— 不入 OpenAPI
    '/api/v1/viewer/auth/refresh': {
      post: {
        tags: ['观众鉴权'],
        summary: '刷新双票',
        operationId: 'viewerAuthRefresh',
        security: [],
        requestBody: {
          required: true,
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/ViewerAuthRefreshRequest' } },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseViewerTokenPair'),
      },
    },
    '/api/v1/viewer/auth/user-info': {
      get: {
        tags: ['观众鉴权'],
        summary: '当前观众',
        operationId: 'viewerAuthUserInfo',
        security: viewerBearer,
        responses: ok200('#/components/schemas/ApiResponseViewerUserInfo'),
      },
    },
    '/api/v1/viewer/profile/update': {
      put: {
        tags: ['观众资料'],
        summary: '更新昵称/头像',
        operationId: 'viewerProfileUpdate',
        security: viewerBearer,
        requestBody: {
          required: true,
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/ViewerProfileUpdateRequest' } },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseViewerUserInfo'),
      },
    },
    '/api/v1/viewer/uploads/file': {
      post: {
        tags: ['观众上传'],
        summary: '中转上传',
        operationId: 'viewerUploadFile',
        security: viewerBearer,
        parameters: [
          { name: 'biz', in: 'query', required: true, schema: { type: 'integer', enum: [1, 2], description: '1 头像 / 2 意见反馈截图' } },
        ],
        requestBody: {
          required: true,
          content: {
            'multipart/form-data': {
              schema: {
                type: 'object',
                required: ['file', 'objectKey'],
                properties: {
                  file: { type: 'string', format: 'binary' },
                  objectKey: { type: 'string' },
                  contentType: { type: 'string' },
                },
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseViewerUploadFile'),
      },
    },
    '/api/v1/viewer/uploads/tos-sts': {
      get: {
        tags: ['观众上传'],
        summary: '直传 STS',
        operationId: 'viewerUploadTosSts',
        security: viewerBearer,
        responses: ok200('#/components/schemas/ApiResponseViewerTosSts'),
      },
    },
    '/api/v1/viewer/feedback/submit': {
      post: {
        tags: ['意见反馈'],
        summary: '提交反馈',
        operationId: 'viewerFeedbackSubmit',
        security: viewerBearer,
        requestBody: {
          required: true,
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/ViewerFeedbackSubmitRequest' } },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseViewerFeedbackId'),
      },
    },
    '/api/v1/viewer/feedback/list': {
      get: {
        tags: ['意见反馈'],
        summary: '我的反馈列表',
        operationId: 'viewerFeedbackList',
        security: viewerBearer,
        parameters: [...pageParams],
        responses: ok200('#/components/schemas/ApiResponseViewerFeedbackList'),
      },
    },
    '/api/v1/viewer/feedback/detail/{id}': {
      get: {
        tags: ['意见反馈'],
        summary: '反馈详情',
        operationId: 'viewerFeedbackDetail',
        security: viewerBearer,
        parameters: [
          { name: 'id', in: 'path', required: true, schema: { type: 'integer', format: 'int64' } },
        ],
        responses: ok200('#/components/schemas/ApiResponseViewerFeedbackDetail'),
      },
    },
    '/api/v1/viewer/learning/lives': {
      get: {
        tags: ['我的学习'],
        summary: '我的学习·直播',
        operationId: 'viewerLearningLives',
        security: viewerBearer,
        parameters: [
          ...pageParams,
          { name: 'state', in: 'query', schema: { $ref: '#/components/schemas/ActivityState' } },
        ],
        responses: ok200('#/components/schemas/ApiResponseWatchList'),
      },
    },
    '/api/v1/viewer/learning/courses': {
      get: {
        tags: ['我的学习'],
        summary: '我的学习·课程',
        description:
          '仅 course_user 有效权益（entitlement_status=1 且未按售卖到期）且课程未软删；按获得权益时间倒序。',
        operationId: 'viewerLearningCourses',
        security: viewerBearer,
        parameters: [...pageParams],
        responses: ok200('#/components/schemas/ApiResponseViewerLearningCourses'),
      },
    },
    '/api/v1/viewer/learning/courses/detail/{courseId}': {
      get: {
        tags: ['我的学习'],
        summary: '我的学习·课程详情',
        description:
          '需 course_user 有效权益且课程未软删；返回封面/有效期/简介/详情 HTML、子课程与扁平目录。无权益或不存在 → 400。',
        operationId: 'viewerLearningCourseDetail',
        security: viewerBearer,
        parameters: [
          {
            name: 'courseId',
            in: 'path',
            required: true,
            schema: { ...BUSINESS_UUID },
            description: '本站课程 id（D74）',
          },
        ],
        responses: ok200(
          '#/components/schemas/ApiResponseViewerLearningCourseDetail',
        ),
      },
    },
    '/api/v1/viewer/learning/courses/lessons/{courseId}/{lessonNodeId}': {
      get: {
        tags: ['我的学习'],
        summary: '我的学习·小节内容',
        description:
          '需有效权益；返回直播 activityId 或素材 URL/文章 HTML，及本学员进度。无权益或不存在 → 400。',
        operationId: 'viewerLearningLessonContent',
        security: viewerBearer,
        parameters: [
          {
            name: 'courseId',
            in: 'path',
            required: true,
            schema: { ...BUSINESS_UUID },
            description: '本站课程 id（D74）',
          },
          {
            name: 'lessonNodeId',
            in: 'path',
            required: true,
            schema: { type: 'integer', format: 'int64' },
            description: '小节节点 id',
          },
        ],
        responses: ok200(
          '#/components/schemas/ApiResponseViewerLearningLessonContent',
        ),
      },
    },
    '/api/v1/viewer/presence/ping': {
      post: {
        tags: ['观众鉴权'],
        summary: '访问心跳',
        operationId: 'viewerPresencePing',
        security: viewerBearer,
        requestBody: {
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/ViewerPresencePingRequest' } },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseViewerPresencePing'),
      },
    },
    '/api/v1/watch/activities': {
      get: {
        tags: ['观看'],
        summary: '活动列表（精简）',
        operationId: 'listWatchActivities',
        parameters: [
          ...pageParams,
          {
            name: 'state',
            in: 'query',
            description: '活动状态；不传或 0 表示不过滤',
            schema: { $ref: '#/components/schemas/ActivityState' },
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseWatchList'),
      },
    },
    '/api/v1/watch/access/{id}': {
      get: {
        tags: ['观看'],
        summary: '观看售卖门禁状态',
        description:
          '可匿名探态。可选观众 Authorization；加密已解锁则带 X-Activity-Access。返回 accessState（D90/D115）。未登录含免费场亦为 need_login；真正拿 summary/records/tos 须登录。',
        operationId: 'getWatchAccess',
        security: [],
        parameters: [watchId, activityAccessHeader],
        responses: ok200('#/components/schemas/ApiResponseWatchAccess'),
      },
    },
    '/api/v1/watch/access/verify-password/{id}': {
      post: {
        tags: ['观看'],
        summary: '校验观看密码并发本场访问 JWT',
        description:
          '须已登录（观众 Authorization）；未登录 → 403「请先登录」。仅 sellAlone=true 且 sellType=encrypted；密码错 → 400；已过期 → 403（D90/D115）。',
        operationId: 'verifyWatchAccessPassword',
        security: viewerBearer,
        parameters: [watchId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/WatchAccessVerifyPasswordRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseWatchAccessVerifyPassword'),
      },
    },
    '/api/v1/watch/detail/{id}': {
      get: {
        tags: ['观看'],
        summary: '观看嵌入页地址',
        description:
          '独立用途的嵌入 URL（兜底）；观看默认走 /watch/summary + /watch/sdk-sign（D94/D95 签名不再回 embedUrl）。',
        operationId: 'getWatchEmbed',
        parameters: [watchId, ...guestParams],
        responses: ok200('#/components/schemas/ApiResponseWatchEmbed'),
      },
    },
    '/api/v1/watch/summary/{id}': {
      get: {
        tags: ['观看'],
        summary: '观看页活动摘要（页面壳）',
        description: [
          '观看页 / 中控台 / 回放页的页面壳：标题、态、时间、回放开关与有效期（D95/D106）。',
          '响应取本库 Activity（对账写库后），不含 SDK 签名。',
          'provider=vhall 且有 provider_room_id 时：拉微吼 webinar/info，用 webinar_state 对账本库 state；不一致则写库。',
          '结束类 state（3/4/5）再跑与管理端回放列表同源的 get-list → pending_package → check/TOS 补偿（D88）。',
          '非 vhall / 无 room id 跳过；云侧失败降级本库、不 500。勿用 /watch/detail 顶替。须过售卖门禁，未过 → 403。',
        ].join('\n'),
        operationId: 'getWatchSummary',
        parameters: [watchId, activityAccessHeader],
        responses: ok200('#/components/schemas/ApiResponseWatchSummary'),
      },
    },
    '/api/v1/watch/sdk-sign': {
      get: {
        tags: ['观看'],
        summary: '观看 SDK 签名与初始化载荷',
        description: [
          '旧路径 GET /api/v1/watch/sdk/{id} 已删除。',
          'Query 仅 id + 可选 nickname；viewerId 只从 Authorization 观众 JWT 解析（D96）；无有效 JWT → 400「请先登录」。',
          '仅返回 id / provider / viewerId / nickname / sdk（D95）；页壳字段见 /watch/summary/{id}。',
          '不下发 guestId / scriptUrl / jqueryUrl / embedUrl / title / state / playback*（D92/D93/D94/D95）。',
          '签名路径不调微吼 fetchRoom。未过售卖门禁 → 403。',
          '头像链接拼接（sdk.head）：取 viewer.avatar_url 绝对地址（去空白）；空白则用默认底座 https://qklive-testcos.cjs99.com/viewer/avatar/default-avatar.png（ZHIBO_DEFAULT_AVATAR_URL / zhibo.default-avatar-url）。本接口不做「CDN 域名 + 相对 objectKey」拼接。统一去掉 #fragment 与已有 v 参数后追加 ?v={viewerId}（已有其它 query 则 &v=）；响应始终非空并参与签名。前端 new VhallSDK 原样传 head（D111）。',
        ].join('\n'),
        operationId: 'getWatchSdkSign',
        security: viewerBearer,
        parameters: [...sdkSignParams, activityAccessHeader],
        responses: ok200('#/components/schemas/ApiResponseWatchSdk'),
      },
    },
    '/api/v1/watch/products/{id}': {
      get: {
        tags: ['观看'],
        summary: '本场商品元数据',
        description:
          '本场绑定 spu 快照（小黄车）。须过售卖门禁（含免费场须登录，D115），未过 → 403。活动不存在或已软删 → 400。',
        operationId: 'getWatchProducts',
        parameters: [watchId, activityAccessHeader],
        responses: ok200('#/components/schemas/ApiResponseWatchProducts'),
      },
    },
    '/api/v1/watch/coupons/{id}': {
      get: {
        tags: ['观看'],
        summary: '本场已发优惠券列表',
        description:
          '读本站 activity_coupon。list 非空则观看/回放侧栏显示「券」悬浮图标；点开弹层选券后，前端直连订单中心 POST /order-center/api/live/viewer/coupon/receive 领取（观众 JWT，见订单中心对接）。' +
          '须过售卖门禁，未过 → 403。活动不存在或已软删 → 400。',
        operationId: 'getWatchCoupons',
        parameters: [watchId, activityAccessHeader],
        responses: ok200('#/components/schemas/ApiResponseWatchCoupons'),
      },
    },
    '/api/v1/watch/coupon-history/{id}': {
      get: {
        tags: ['观看'],
        summary: '观看端优惠券聊天历史',
        description:
          '读本站 room_comment（comment_type=3、未软删），按 comment_time 旧→新，最多 500 条；与微吼聊天按时间合并渲染券卡片。' +
          '须过售卖门禁，未过 → 403。中控台同源见 /activities/console/coupon-history。',
        operationId: 'getWatchCouponHistory',
        parameters: [watchId, activityAccessHeader],
        responses: ok200('#/components/schemas/ApiResponseWatchCouponHistory'),
      },
    },
    '/api/v1/watch/chat-history/{id}': {
      get: {
        tags: ['观看'],
        summary: '观看端聊天历史（回放只读）',
        description:
          '回放页只读普通聊天；代理微吼 get-list。须过售卖门禁，未过 → 403。page 从 1 起，每页 50（D91）。问答另见 question-history。',
        operationId: 'getWatchChatHistory',
        parameters: [
          watchId,
          activityAccessHeader,
          {
            name: 'page',
            in: 'query',
            schema: { type: 'integer', minimum: 1, default: 1 },
            description: '从 1 起；缺省 1；每页 50 条',
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseWatchChatHistory'),
      },
    },
    '/api/v1/watch/question-history/{id}': {
      get: {
        tags: ['观看'],
        summary: '观看端问答历史（本站提问行）',
        description:
          '读本站 room_comment（comment_type=提问、未软删），按 comment_time 旧→新，最多 500 条。' +
          '回放页直接用（不挂观看 SDK）；直播页在微吼 vhall_get_live_history_question_msg 空/失败后兜底。' +
          '须过售卖门禁，未过 → 403（D91）。中控台同源兜底见 /activities/console/question-history。',
        operationId: 'getWatchQuestionHistory',
        parameters: [watchId, activityAccessHeader],
        responses: ok200('#/components/schemas/ApiResponseWatchQuestionHistory'),
      },
    },
    '/api/v1/watch/records/{id}': {
      get: {
        tags: ['观看'],
        summary: '可播回放列表',
        description:
          '每次先跑与管理端回放列表同源的 get-list diff → pending_package → check/TOS 补偿（D88）；' +
          '再只返回本库 ready 且 tosUrl 非空的条目。未过售卖门禁 → 403（D89/D90）。补偿失败只打日志，仍回本库可播列表。',
        operationId: 'listWatchPlayRecords',
        parameters: [watchId, activityAccessHeader],
        responses: ok200('#/components/schemas/ApiResponseWatchPlayRecords'),
      },
    },
    '/api/v1/watch/replay/start/{id}': {
      post: {
        tags: ['回放'],
        summary: '回放开始',
        operationId: 'watchReplayStart',
        security: viewerBearer,
        parameters: [watchId],
        requestBody: {
          required: true,
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/WatchReplayStartRequest' } },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseWatchReplayStart'),
      },
    },
    '/api/v1/watch/replay/heartbeat/{id}': {
      post: {
        tags: ['回放'],
        summary: '回放心跳',
        operationId: 'watchReplayHeartbeat',
        security: viewerBearer,
        parameters: [watchId],
        requestBody: {
          required: true,
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/WatchReplayHeartbeatRequest' } },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseWatchReplayHeartbeat'),
      },
    },
    // 仅本地：POST /api/v1/watch/dev/presence/{id}、/watch/comments/report|question-answer —— 不入 OpenAPI（D104）
    '/api/v1/watch/comments/delete/{id}': {
      post: {
        tags: ['评论'],
        summary: '软删评论',
        description:
          '须观众 Authorization；无票 → 401。Body 传 commentId（本次上报返回的本站 id）或 msgId（仅历史行）。',
        operationId: 'watchCommentDelete',
        security: viewerBearer,
        parameters: [watchId],
        requestBody: {
          required: true,
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/WatchCommentDeleteRequest' } },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseIntId'),
      },
    },
    '/api/v1/watch/audience/salesman/{id}': {
      get: {
        tags: ['用户运营'],
        summary: '本场业务员查询',
        description: [
          '须观众 Authorization；观众 id 只从 JWT 取；无票 → 401。路径 {id}=活动 UUID；活动不存在/已软删 → 400。',
          '读本场 activity_audience 当前观众行：优先 employee_inviter_id（员工邀请人），否则 importer_id（导入人）；无行或两字段皆空 → salesmanId=null。',
          '观看页创建订单前调用，有值则把 salesmanId 字符串写入订单中心 POST …/viewer/order/create。',
        ].join('\n'),
        operationId: 'watchAudienceSalesman',
        security: viewerBearer,
        parameters: [watchId],
        responses: ok200('#/components/schemas/ApiResponseWatchAudienceSalesman'),
      },
    },
    '/api/v1/watch/audience/attribute/{id}': {
      post: {
        tags: ['用户运营'],
        summary: '邀请归因',
        description: [
          '须观众 Authorization（D97）；被邀请观众 id 只从 JWT 取，Body 不传 viewerId；无票 → 401。',
          '区分邀请人类型 inviterKind：employee 员工 / audience 观众。',
          'employee：写 activity_audience.employee_inviter_*（空则填，先导入后邀请可补填）。',
          'audience：本站不记录观众邀请 → 400「不记录观众邀请」（D77；分支保留）。',
          '缺省 inviterKind=employee。',
        ].join('\n'),
        operationId: 'watchAudienceAttribute',
        security: viewerBearer,
        parameters: [watchId],
        requestBody: {
          required: true,
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/WatchAudienceAttributeRequest' } },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseWatchAudienceAttribute'),
      },
    },
    '/api/v1/watch/course/enroll/{id}': {
      post: {
        tags: ['用户运营'],
        summary: '课程分享链进房 enroll',
        description: [
          '路径 {id}=课程 UUID。须观众 JWT；viewerId 只从 JWT 取，Body 不接受 viewerId。',
          '无行则插入（entitlement_status=1，acquire_way=purchase；按售卖写 expire）并 total_user_count+1；已有行不改权益/到期。',
          '课程不存在/已软删或观众不存在 → 400；无票 → 401。',
        ].join(''),
        operationId: 'watchCourseEnroll',
        security: viewerBearer,
        parameters: [
          {
            name: 'id',
            in: 'path',
            required: true,
            schema: { ...BUSINESS_UUID },
            description: '本站课程 id（D74）',
          },
        ],
        requestBody: {
          required: false,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/WatchCourseEnrollRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseWatchCourseEnroll'),
      },
    },
    '/api/v1/callbacks/vhall/presence': {
      post: {
        tags: ['回调'],
        summary: '微吼上下线回调',
        description:
          '配置到微吼回调：POST /api/v1/callbacks/vhall/presence。微吼主动调用，不是前端调用。method_id 须为 20；校签失败 403；成功返回纯文本 success。写入本站进出名单与流水。',
        operationId: 'vhallPresenceCallback',
        security: [],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/VhallPresenceCallback' },
            },
          },
        },
        responses: plainTextSuccess200(),
      },
    },
    '/api/v1/callbacks/vhall/live-state': {
      post: {
        tags: ['回调'],
        summary: '微吼开播/结束回调',
        description:
          '配置到微吼回调：POST /api/v1/callbacks/vhall/live-state。method_id=1；type 1 开播 / 3 结束；校签失败 403；成功纯文本 success。写回本站活动 state。',
        operationId: 'vhallLiveStateCallback',
        security: [],
        responses: plainTextSuccess200(),
      },
    },
    '/api/v1/callbacks/vhall/transcode': {
      post: {
        tags: ['回调'],
        summary: '微吼转码回调',
        description:
          '配置到微吼回调：POST /api/v1/callbacks/vhall/transcode。进度 method_id=38、完成 method_id=3；校签失败 403；成功纯文本 success。写素材转码字段。',
        operationId: 'vhallTranscodeCallback',
        security: [],
        responses: plainTextSuccess200(),
      },
    },
    '/api/v1/callbacks/vhall/record-generate': {
      post: {
        tags: ['回调'],
        summary: '微吼回放生成回调',
        description:
          '配置到微吼回调：POST /api/v1/callbacks/vhall/record-generate。method_id=4；校签失败 403；成功纯文本 success。回放元数据落 activity_record。',
        operationId: 'vhallRecordGenerateCallback',
        security: [],
        responses: plainTextSuccess200(),
      },
    },
    '/api/v1/callbacks/vhall/record-download': {
      post: {
        tags: ['回调'],
        summary: '微吼回放打包完成回调',
        description:
          '配置到微吼回调：POST /api/v1/callbacks/vhall/record-download。method_id=6；校签失败 403；成功先回纯文本 success，再异步下载到 TOS。',
        operationId: 'vhallRecordDownloadCallback',
        security: [],
        responses: plainTextSuccess200(),
      },
    },
    // D110：中控台名单 / 走势 / 评论审核已迁 admin `/activities/*`；live 不再提供 watch/attendees|presence-trend|chat-filter
  }


  finalizeSchemas(schemas)

  return {
    openapi: '3.0.3',
    info: {
      title: 'zhibo live-service',
      description: [
        '钱坤直播观看端 API（live-service :8082）。中控台已迁 admin-service（D110）。',
        '',
        '契约来源：`zhibo-shared/docs/API约定.md`（D58/D61）。',
        '本文件供 Apifox 导入，也是正式后端按文档反向实现的权威输入之一。',
        '',
        '**通则**',
        '- 成功：`code === 0`（HTTP 仍多为 200）',
        '- 活动路径 `{id}` 均为本站 **32 位业务 UUID（D74）**',
        '- 时间：`yyyy-MM-dd HH:mm:ss`',
        '- JSON 不输出 `null` 字段',
        '- 观看端不走管理端 JWT',
      ].join('\n'),
      version: '2.8.0',
      contact: { name: 'zhibo-backend' },
    },
    servers: [
      { url: 'http://127.0.0.1:8082', description: '本地 live-service' },
    ],
    tags: [
      { name: '观看' },
      { name: '观众鉴权' },
      { name: '观众资料' },
      { name: '观众上传' },
      { name: '意见反馈' },
      { name: '我的学习' },
      { name: '回放' },
      { name: '评论' },
      { name: '用户运营' },
      { name: '开发' },
      { name: '中控台' },
      { name: '回调' },
    ],
    paths,
    components: {
      securitySchemes: {
        BearerViewer: {
          type: 'http',
          scheme: 'bearer',
          bearerFormat: 'JWT',
          description: '观众 access JWT；不要带 refresh / 本场访问 token',
        },
      },
      parameters: {
        WatchActivityId: {
          name: 'id',
          in: 'path',
          required: true,
          schema: {
            ...BUSINESS_UUID,
            example: 'a1b2c3d4e5f67890abcdef1234567890',
          },
          description: '本站活动 id（D74）',
        },
      },
      schemas,
    },
  }
}

function mergeDocs(admin, live) {
  const prefixTags = (doc, prefix) =>
    (doc.tags || []).map((t) => ({ ...t, name: `${prefix}/${t.name}` }))

  const prefixPathTags = (paths, prefix) => {
    const out = {}
    for (const [p, item] of Object.entries(paths)) {
      out[p] = {}
      for (const [method, op] of Object.entries(item)) {
        if (op && typeof op === 'object' && op.tags) {
          out[p][method] = {
            ...op,
            tags: op.tags.map((t) => `${prefix}/${t}`),
            operationId: `${prefix}_${op.operationId}`,
          }
        } else {
          out[p][method] = op
        }
      }
    }
    return out
  }

  // live paths don't collide with admin except conceptually; keep both
  const livePaths = {}
  for (const [p, item] of Object.entries(live.paths)) {
    livePaths[p] = item
  }

  return {
    openapi: '3.0.3',
    info: {
      title: 'zhibo API（admin + live）',
      description: [
        '钱坤直播全站 OpenAPI，供 Apifox 一次导入。',
        '',
        '- admin-service：`http://127.0.0.1:8081`',
        '- live-service：`http://127.0.0.1:8082`',
        '',
        '契约：`zhibo-shared/docs/API约定.md`（D58 中文注释/枚举；D61 落库类型与长度）。',
        '本文件亦是正式后端按文档反向实现接口与建表/校验的权威输入之一。',
        '分服务文件：`admin-service.openapi.yaml` / `live-service.openapi.yaml`。',
        '',
        '导入后请在 Apifox 按 Tag 前缀区分服务；调试时切换对应 Server。',
      ].join('\n'),
      version: '2.8.0',
      contact: { name: 'zhibo' },
    },
    servers: [
      { url: 'http://127.0.0.1:8081', description: 'admin-service' },
      { url: 'http://127.0.0.1:8082', description: 'live-service' },
    ],
    tags: [...prefixTags(admin, 'admin'), ...prefixTags(live, 'live')],
    paths: {
      ...prefixPathTags(admin.paths, 'admin'),
      ...prefixPathTags(livePaths, 'live'),
    },
    components: {
      securitySchemes: {
        ...(admin.components.securitySchemes || {}),
        ...(live.components.securitySchemes || {}),
      },
      parameters: {
        ...(admin.components.parameters || {}),
        ...(live.components.parameters || {}),
      },
      schemas: {
        ...admin.components.schemas,
        // live-only schemas that may not exist on admin
        ...Object.fromEntries(
          Object.entries(live.components.schemas).filter(
            ([k]) => !admin.components.schemas[k],
          ),
        ),
      },
    },
    security: admin.security,
  }
}

function writeDoc(baseName, doc) {
  const jsonPath = path.join(__dirname, `${baseName}.openapi.json`)
  const yamlPath = path.join(__dirname, `${baseName}.openapi.yaml`)
  fs.writeFileSync(jsonPath, JSON.stringify(doc, null, 2) + '\n', 'utf8')
  fs.writeFileSync(yamlPath, toYaml(doc), 'utf8')
  const pathCount = Object.keys(doc.paths || {}).length
  console.log(`wrote ${baseName}: ${pathCount} paths →`)
  console.log(`  ${yamlPath}`)
  console.log(`  ${jsonPath}`)
}

/** 对外课程片段：同步到 zhibo-shared/docs/openapi/outside-courses.yaml（供 Apifox 单接口导入） */
function writeSharedOutsideCourses(adminDoc) {
  const sharedDir = path.resolve(
    __dirname,
    '../../../zhibo-shared/docs/openapi',
  )
  fs.mkdirSync(sharedDir, { recursive: true })
  const pathItem = adminDoc.paths['/api/v1/outside/courses/list']
  if (!pathItem) {
    throw new Error('admin doc missing /api/v1/outside/courses/list')
  }
  const schemaNames = [
    'BusinessUuid',
    'ApiResponseBase',
    'OutsideCourseListRequest',
    'OutsideCourseItem',
    'OutsideCourseListData',
    'ApiResponseOutsideCourseList',
  ]
  const schemas = {}
  for (const name of schemaNames) {
    if (!adminDoc.components.schemas[name]) {
      throw new Error(`missing schema for shared outside export: ${name}`)
    }
    schemas[name] = adminDoc.components.schemas[name]
  }
  const frag = {
    openapi: '3.0.3',
    info: {
      title: '对外课程列表',
      version: adminDoc.info.version,
      description: [
        '对外课程列表，只包含 POST /api/v1/outside/courses/list。',
        '当前不鉴权，不需要登录，也不要 token。域名白名单后续再做。',
        '只返回已上架且风控已通过、未软删的课程。',
        '列表项字段：courseId / courseName / contentCount / departmentName?。',
        '旧版 GET + keyword + id/title/owners 已废止。',
      ].join('\n'),
    },
    servers: adminDoc.servers,
    tags: [{ name: '对外课程' }],
    paths: {
      '/api/v1/outside/courses/list': pathItem,
    },
    components: { schemas },
  }
  const yamlPath = path.join(sharedDir, 'outside-courses.yaml')
  const jsonPath = path.join(sharedDir, 'outside-courses.json')
  fs.writeFileSync(yamlPath, toYaml(frag), 'utf8')
  fs.writeFileSync(jsonPath, JSON.stringify(frag, null, 2) + '\n', 'utf8')
  console.log(`wrote shared outside-courses →`)
  console.log(`  ${yamlPath}`)
  console.log(`  ${jsonPath}`)
}

const admin = buildAdmin()
const live = buildLive()
attachApifoxEnums(admin)
attachApifoxEnums(live)
const all = mergeDocs(admin, live)

writeDoc('admin-service', admin)
writeDoc('live-service', live)
writeDoc('zhibo', all)
writeSharedOutsideCourses(admin)

console.log('\nApifox 导入建议：优先导入 zhibo.openapi.yaml（或 .json）一次搞定。')
