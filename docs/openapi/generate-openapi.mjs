/**
 * 按 zhibo-shared/docs/API约定.md 生成 OpenAPI 3.0.3
 * 用法：node generate-openapi.mjs
 *
 * D65：平时禁止改本脚本、禁止跑导出。仅用户明确要求「重新生成 / 导出 OpenAPI」时：
 * 先删除旧 *.openapi.yaml / *.openapi.json，再按当时 API约定 + 代码全量重写本脚本并导出；
 * 不要在旧 yaml 上打补丁，也不要日常攒着改本文件。
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
  listedTime: '上架时间，yyyy-MM-dd HH:mm:ss',
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
  autoList: '风控通过后是否自动上架',
  sellAlone: '是否单独售卖',
  price: '售价（元）',
  linePrice: '划线价（元）',
  validityLabel: '有效期展示文案',
  playbackEnabled: '是否开启回放',
  playbackValidityDays: '回放有效天数',
  playbackSpeedAllow: '是否允许倍速',
  playbackFastForwardAllow: '是否允许快进',
  accessValidityDays: '购买后有效天数',
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
  'ActivityRecordDownloadData.status':
    '打包状态：0 未开始 / 1 已完成 / 2 进行中 / 3 失败',
  'ActivityRecordDownloadData.progress': '打包进度 0～100',
  'ActivityRecordDownloadData.downloadUrl':
    '下载地址；status=1 时可用（微吼侧约 7 天有效）',
  'LiveReportResult.id': '报备单 UUID',
  'LiveReportDetail.id': '报备单 UUID',
  'LiveReportAuditItem.id': '审核记录 UUID',
  'CourseSummary.id': '课程 UUID',
  'CourseMaterial.id': '素材 UUID',
  'OutsideCourseItem.id': '课程 UUID',
  'WatchActivitySummary.id': '本站活动 UUID',
  'WatchEmbedData.id': '本站活动 UUID',
  'WatchSdkData.id': '本站活动 UUID',
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
  'WatchSdkData.sdk': '观看 JS SDK 初始化参数',
}

/** 按字段名补落库类型 / 长度（D61）；已有 maxLength 的保留。 */
const FIELD_DB_BY_NAME = {
  id: { db: 'VARCHAR(36)', maxLength: 36, format: 'uuid' },
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
  groupId: { db: 'VARCHAR(36)', maxLength: 36, format: 'uuid' },
  reportId: { db: 'VARCHAR(36)', maxLength: 36, format: 'uuid' },
  coverMaterialId: { db: 'VARCHAR(36)', maxLength: 36, format: 'uuid' },
  materialId: { db: 'VARCHAR(36)', maxLength: 36, format: 'uuid' },
  recordMaterialId: { db: 'VARCHAR(36)', maxLength: 36, format: 'uuid' },
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
  'OutsideCourseItem.title': { db: 'VARCHAR(45)', maxLength: 45 },
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

/**
 * @param fromPath 路径特例（可表达「API maxLength≠库列长度」，如昵称校验 10 / 库 VARCHAR(64)）
 */
function inferDbLabel(prop, key, hint, fromPath) {
  if (!prop || prop.$ref) return null
  // 路径特例：库类型原文始终听 hint（校验长度可严于库列）
  if (fromPath && hint && hint.db) return hint.db
  // 按字段名提示：与已有 maxLength 冲突时改按 maxLength 推断（避免 content 默认 500 盖住文章 100000）
  if (hint && hint.db) {
    const maxConflict =
      typeof prop.maxLength === 'number' &&
      typeof hint.maxLength === 'number' &&
      prop.maxLength !== hint.maxLength
    if (!maxConflict) return hint.db
  }
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
  if (hint && hint.db) return hint.db
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

        if (!prop.$ref && hint) {
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
      description: 'free 免费 / paid 付费 / encrypted 加密 / specified 指定学员',
    },
    AccessValidityType: {
      type: 'string',
      enum: ['long', 'days'],
      description: 'long 长期有效 / days 购买后 N 天有效',
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
      properties: { id: { type: 'string', format: 'uuid' } },
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
        id: { type: 'string', format: 'uuid' },
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
          description: 'data:image/png;base64,... 可直接作 img src',
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
        id: { type: 'string', format: 'uuid' },
        name: { type: 'string', maxLength: 64 },
        count: { type: 'integer' },
        isDefault: { type: 'boolean' },
      },
    },
    MaterialItem: {
      type: 'object',
      properties: {
        id: { type: 'string', format: 'uuid' },
        type: { $ref: '#/components/schemas/MaterialType' },
        groupId: { type: 'string', format: 'uuid' },
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
        groupId: { type: 'string', format: 'uuid' },
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
        groupId: { type: 'string', format: 'uuid' },
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
        id: { type: 'string', format: 'uuid' },
        groupId: { type: 'string', format: 'uuid' },
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
        'autoList',
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
          type: 'string',
          format: 'uuid',
          maxLength: 36,
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
        autoList: {
          type: 'boolean',
          description: '风控通过后是否自动上架；创建时 shelfStatus 一律 pending',
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
          items: { type: 'string', format: 'uuid' },
        },
        reportId: {
          type: 'string',
          format: 'uuid',
          maxLength: 36,
          description:
            '创建必填。绑定已通过直播报备 UUID。须 status=passed、未软删；hostIds 须恰好为该报备主播 1 人。创建后不可改绑。库：VARCHAR(36)',
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
        autoList: { type: 'boolean' },
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
          items: { type: 'string', format: 'uuid' },
        },
        recordMaterialId: {
          type: 'string',
          format: 'uuid',
          maxLength: 36,
          description:
            '仅 type=4：替换录播素材（与创建/详情同名 recordMaterialId）。写入后 ready→0；库：VARCHAR(36)',
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
        sellAlone: { type: 'boolean' },
        sellType: { $ref: '#/components/schemas/SellType' },
        price: {
          type: 'number',
          multipleOf: 0.01,
          description: 'paid 时必填；元，最多 2 位小数',
        },
        linePrice: {
          type: 'number',
          multipleOf: 0.01,
          description: '须 > price；非 paid 忽略',
        },
        password: {
          type: 'string',
          pattern: '^\\d{6}$',
          description: 'encrypted 时必填；明文 6 位数字',
        },
        accessValidityType: { $ref: '#/components/schemas/AccessValidityType' },
        accessValidityDays: { type: 'integer', minimum: 1 },
      },
    },
    SalesSettingsData: {
      type: 'object',
      properties: {
        id: { type: 'string', format: 'uuid' },
        sellType: { $ref: '#/components/schemas/SellType' },
        validityLabel: { type: 'string' },
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
        id: { type: 'string', format: 'uuid' },
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
        id: { type: 'string', format: 'uuid' },
        riskStatus: { $ref: '#/components/schemas/RiskStatus' },
        shelfStatus: { $ref: '#/components/schemas/ShelfStatus' },
      },
    },
    AuditRecord: {
      type: 'object',
      properties: {
        id: { type: 'string', format: 'uuid' },
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
        id: { type: 'string', format: 'uuid' },
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
          type: 'string',
          format: 'uuid',
          maxLength: 36,
          description:
            '本场绑定的录播素材 UUID（与创建/改活动请求同名）；非录播或不绑可不返回；库：VARCHAR(36)',
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
            autoList: { type: 'boolean' },
            reportId: {
              type: 'string',
              format: 'uuid',
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
      required: ['recordId', 'status'],
      description:
        '回放下载：微吼 check-download；对应管理后台回放列表「下载」',
      properties: {
        recordId: {
          type: 'integer',
          format: 'int64',
          description: '微吼回放 id（同 GET .../records 的 list[].id）',
          example: 5814669,
        },
        downloadUrl: {
          type: 'string',
          format: 'uri',
          description:
            '下载地址；status=1 时可用（微吼侧约 7 天有效）',
          example: 'https://vod-download.vhall.com/....zip',
        },
        status: {
          type: 'integer',
          description: '打包状态：0 未开始 / 1 已完成 / 2 进行中 / 3 失败',
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
      },
    },
    EndActivityData: {
      type: 'object',
      required: ['id', 'state'],
      description:
        '结束直播 / 第三方推流开始直播（/activities/start）共用：返回本站活动 id 与写回后的 state',
      properties: {
        id: { type: 'string', format: 'uuid', description: '本站活动 UUID' },
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
        id: { type: 'string', format: 'uuid' },
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
        id: { type: 'string', format: 'uuid' },
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
        id: { type: 'string', format: 'uuid' },
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
        coverMaterialId: { type: 'string', format: 'uuid' },
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
        coverMaterialId: { type: 'string', format: 'uuid' },
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
        id: { type: 'string', format: 'uuid' },
        status: { $ref: '#/components/schemas/LiveReportStatus' },
      },
    },
    LiveReportDetail: {
      type: 'object',
      properties: {
        id: { type: 'string', format: 'uuid' },
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
        coverMaterialId: { type: 'string', format: 'uuid' },
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
        id: { type: 'string', format: 'uuid' },
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
        id: { type: 'string', format: 'uuid' },
        name: { type: 'string' },
        url: { type: 'string' },
        type: { $ref: '#/components/schemas/MaterialFileType' },
        fileSize: { type: 'integer', format: 'int64' },
        mimeType: { type: 'string' },
      },
    },
    CreateCourseRequest: {
      type: 'object',
      required: ['title', 'coverMaterialId', 'autoList', 'ownerIds'],
      properties: {
        title: { type: 'string', maxLength: 45 },
        introduction: { type: 'string', maxLength: 256 },
        detailHtml: {
          type: 'string',
          maxLength: 200000,
          description: '可见文字 ≤10000，原文 ≤200000',
        },
        coverMaterialId: {
          type: 'string',
          format: 'uuid',
          description: '两审通过的 JPG/PNG 图片素材，≤5MB',
        },
        autoList: { type: 'boolean' },
        ownerIds: {
          type: 'array',
          minItems: 1,
          maxItems: 20,
          items: { $ref: '#/components/schemas/EmployeeId' },
          description: '在职员工 id（JSON 数字，D56）',
        },
        materialIds: {
          type: 'array',
          maxItems: 100,
          items: { type: 'string', format: 'uuid' },
          description: '课件：两审通过的 image/video/document，不含 article',
        },
      },
    },
    CourseSummary: {
      type: 'object',
      properties: {
        id: { type: 'string', format: 'uuid' },
        title: { type: 'string' },
        coverUrl: { type: 'string' },
        shelfStatus: { $ref: '#/components/schemas/ShelfStatus' },
        riskStatus: { $ref: '#/components/schemas/RiskStatus' },
        autoList: { type: 'boolean' },
        listedTime: { type: 'string', description: '无则不返回' },
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
          description: '内容数与总销量均为 0',
        },
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
            coverMaterialId: { type: 'string', format: 'uuid' },
            materials: {
              type: 'array',
              items: { $ref: '#/components/schemas/CourseMaterial' },
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
    OutsideCourseOwner: {
      type: 'object',
      properties: {
        id: { $ref: '#/components/schemas/EmployeeId' },
        userName: { type: 'string', description: '员工姓名' },
      },
    },
    OutsideCourseItem: {
      type: 'object',
      properties: {
        id: { type: 'string', format: 'uuid' },
        title: { type: 'string' },
        coverUrl: { type: 'string', description: '空则不返回' },
        introduction: { type: 'string', description: '空则不返回' },
        listedTime: { type: 'string', description: '空则不返回' },
        updatedTime: { type: 'string' },
        owners: {
          type: 'array',
          description: '没有时为 []，不省略',
          items: { $ref: '#/components/schemas/OutsideCourseOwner' },
        },
      },
    },
    OutsideCourseListData: {
      type: 'object',
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
    ApiResponseOutsideCourseList: {
      $ref: '#/components/schemas/OutsideCourseListData',
    },
  })) {
    schemas[name] = wrapData(data)
  }

  const activityId = { $ref: '#/components/parameters/ActivityId' }
  const materialId = { $ref: '#/components/parameters/MaterialId' }
  const courseId = { $ref: '#/components/parameters/CourseId' }
  const liveReportId = { $ref: '#/components/parameters/LiveReportId' }

  const paths = {
    '/api/v1/auth/captcha': {
      get: {
        tags: ['登录鉴权'],
        summary: '图形验证码',
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
            schema: { type: 'string', format: 'uuid' },
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
                  groupId: { type: 'string', format: 'uuid' },
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
                    autoList: true,
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
          '某场活动下一条回放的打包下载地址（管理后台回放「下载」）。路径 id=本站活动 UUID；recordId=回放列表 list[].id。走微吼 check-download。非 vhall / 未接入 → 501。status=1 时 downloadUrl 可用（约 7 天有效）。',
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
        description: '与 live GET /api/v1/watch/presence-trend/{id} 同形。',
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
    '/api/v1/activities/attendees/{id}': {
      get: {
        tags: ['中控台'],
        summary: '中控台进出名单（废弃）',
        description:
          '废弃：新代码一律用 live GET /api/v1/watch/attendees/{id}。实现可暂时保留。',
        deprecated: true,
        operationId: 'listActivityAttendeesDeprecated',
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
        description: '成功后 riskStatus 与 shelfStatus 均为 pending，并写一条 action=submit。',
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
    '/api/v1/courses/list': {
      get: {
        tags: ['课程'],
        summary: '课程列表',
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
        description: '不存在或已软删 → 400。',
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
        description: '仅内容数与总销量均为 0，否则 400。',
        operationId: 'deleteCourse',
        security: bearer,
        parameters: [courseId],
        responses: ok200('#/components/schemas/ApiResponseId'),
      },
    },
    '/api/v1/courses/shelf/{id}': {
      post: {
        tags: ['课程'],
        summary: '课程上架 / 下架',
        description:
          '上架要求风控已通过且当前为待上架或已下架，并更新 listedTime。下架仅已上架。强制封禁不可上下架。',
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
        description: '按时间倒序。action 为 submit 或 audit。submit 不返回 result / reason。',
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
        description:
          '通过不要理由。驳回把上架改为 banned。autoList=true 且通过则上架改为 on 并写 listedTime；autoList=false 且当前为 banned 时回到 pending。',
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
    '/api/v1/outside/courses/list': {
      get: {
        tags: ['对外课程'],
        summary: '对外课程列表',
        description:
          '当前不鉴权。只返回已上架且风控已通过、未软删的课程。默认按 listedTime 倒序。',
        operationId: 'listOutsideCourses',
        security: [],
        parameters: [
          ...pageParams,
          {
            name: 'keyword',
            in: 'query',
            schema: { type: 'string' },
            description: '课程名称模糊',
          },
        ],
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
        '- 活动路径 `{id}` 均为本站 **UUID**',
        '- 时间：`yyyy-MM-dd HH:mm:ss`',
        '- JSON 不输出 `null` 字段',
        '- 除登录白名单与 `/api/v1/outside/**` 外需 `Authorization: Bearer <accessToken>`',
      ].join('\n'),
      version: '2.5.0',
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
            type: 'string',
            format: 'uuid',
            example: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
          },
          description: '本站活动 UUID',
        },
        MaterialId: {
          name: 'id',
          in: 'path',
          required: true,
          schema: { type: 'string', format: 'uuid' },
          description: '素材 / 分组 UUID',
        },
        CourseId: {
          name: 'id',
          in: 'path',
          required: true,
          schema: { type: 'string', format: 'uuid' },
          description: '本站课程 UUID',
        },
        LiveReportId: {
          name: 'id',
          in: 'path',
          required: true,
          schema: { type: 'string', format: 'uuid' },
          description: '直播报备 UUID',
        },
      },
      schemas,
    },
    security: bearer,
  }
}

function buildLive() {
  const schemas = {
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
      properties: { id: { type: 'string', format: 'uuid' } },
    },
    WatchActivitySummary: {
      type: 'object',
      properties: {
        id: { type: 'string', format: 'uuid' },
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
    WatchEmbedData: {
      type: 'object',
      properties: {
        id: { type: 'string', format: 'uuid' },
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
      properties: {
        scriptUrl: { type: 'string', format: 'uri' },
        jqueryUrl: { type: 'string', nullable: true },
        account: { type: 'string', description: '回显用；init 不要传' },
        email: { type: 'string' },
        username: { type: 'string' },
        webinarId: { type: 'string' },
        appKey: { type: 'string' },
        signedAt: { type: 'string' },
        signType: { type: 'integer', example: 0 },
        sign: { type: 'string' },
      },
    },
    WatchSdkData: {
      type: 'object',
      properties: {
        id: { type: 'string', format: 'uuid' },
        provider: { $ref: '#/components/schemas/Provider' },
        title: { type: 'string' },
        state: { $ref: '#/components/schemas/ActivityState' },
        type: { $ref: '#/components/schemas/ActivityType' },
        nickname: { type: 'string' },
        guestId: { type: 'string' },
        startTime: { type: 'string' },
        endTime: { type: 'string' },
        sdk: { $ref: '#/components/schemas/VhallSdkPayload' },
        embedUrl: { type: 'string', format: 'uri' },
      },
    },
    AttendanceEnterRequest: {
      type: 'object',
      required: ['userId', 'enterTime'],
      properties: {
        userId: { type: 'string' },
        enterTime: {
          type: 'string',
          example: '2026-09-30 10:00:00',
        },
        mid: { type: 'string' },
        nickname: { type: 'string' },
        avatarUrl: { type: 'string' },
      },
    },
    AttendanceLeaveRequest: {
      type: 'object',
      required: ['items'],
      properties: {
        items: {
          type: 'array',
          minItems: 1,
          maxItems: 100,
          items: {
            type: 'object',
            required: ['userId', 'enterTime', 'leaveTime'],
            properties: {
              userId: { type: 'string' },
              enterTime: { type: 'string' },
              leaveTime: { type: 'string' },
              mid: { type: 'string' },
            },
          },
        },
      },
    },
    AttendanceLeaveData: {
      type: 'object',
      properties: {
        updated: { type: 'integer' },
        skipped: { type: 'integer' },
        missing: { type: 'integer' },
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
        id: { type: 'string', format: 'uuid' },
        startTime: { type: 'string' },
        endTime: { type: 'string' },
        bucketSeconds: { type: 'integer', example: 60 },
        points: {
          type: 'array',
          items: { $ref: '#/components/schemas/PresenceTrendPoint' },
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
    ApiResponseWatchEmbed: { $ref: '#/components/schemas/WatchEmbedData' },
    ApiResponseWatchSdk: { $ref: '#/components/schemas/WatchSdkData' },
    ApiResponseAttendanceLeave: {
      $ref: '#/components/schemas/AttendanceLeaveData',
    },
    ApiResponseAttendeeList: { $ref: '#/components/schemas/AttendeeListData' },
    ApiResponsePresenceTrend: { $ref: '#/components/schemas/PresenceTrend' },
    ApiResponseChatFilter: { $ref: '#/components/schemas/ChatFilterData' },
  })) {
    schemas[name] = wrapData(data)
  }

  const watchId = { $ref: '#/components/parameters/WatchActivityId' }
  const guestParams = [
    {
      name: 'guestId',
      in: 'query',
      schema: { type: 'string', pattern: '^[A-Za-z0-9]+$' },
      description: '只能字母数字；不传则后端生成',
    },
    {
      name: 'nickname',
      in: 'query',
      schema: { type: 'string', maxLength: 50 },
      description: '默认「观众」',
    },
  ]

  const paths = {
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
    '/api/v1/watch/detail/{id}': {
      get: {
        tags: ['观看'],
        summary: '观看嵌入（iframe 兜底）',
        operationId: 'getWatchEmbed',
        parameters: [watchId, ...guestParams],
        responses: ok200('#/components/schemas/ApiResponseWatchEmbed'),
      },
    },
    '/api/v1/watch/sdk/{id}': {
      get: {
        tags: ['观看'],
        summary: '学生端观看 SDK 初始化载荷',
        operationId: 'getWatchSdk',
        parameters: [watchId, ...guestParams],
        responses: ok200('#/components/schemas/ApiResponseWatchSdk'),
      },
    },
    '/api/v1/watch/attendance/enter/{id}': {
      post: {
        tags: ['进出房'],
        summary: '进入（辅通道）',
        description: '前端在 VITE_ATTENDANCE_CLIENT_REPORT=true 时调用。',
        operationId: 'attendanceEnter',
        parameters: [watchId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/AttendanceEnterRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseId'),
      },
    },
    '/api/v1/watch/attendance/leave/{id}': {
      post: {
        tags: ['进出房'],
        summary: '离开（辅通道，批量）',
        operationId: 'attendanceLeave',
        parameters: [watchId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/AttendanceLeaveRequest' },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseAttendanceLeave'),
      },
    },
    '/api/v1/callbacks/vhall/presence': {
      post: {
        tags: ['回调'],
        summary: '微吼上下线回调（主通道）',
        description: '微吼主动 POST，不是前端调用。method_id 须为 20。',
        operationId: 'vhallPresenceCallback',
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/VhallPresenceCallback' },
            },
          },
        },
        responses: {
          '200': { description: '按微吼约定响应' },
        },
      },
    },
    '/api/v1/watch/attendees/{id}': {
      get: {
        tags: ['中控台'],
        summary: '中控台进出名单',
        description: '打开页面、换 Tab、搜索、加载更多时调用；不要轮询。',
        operationId: 'listWatchAttendees',
        parameters: [
          watchId,
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
    '/api/v1/watch/presence-trend/{id}': {
      get: {
        tags: ['中控台'],
        summary: '进出三线走势',
        description: '数据来自 presence_event，不要调微吼 report/online。',
        operationId: 'getWatchPresenceTrend',
        parameters: [
          watchId,
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
    '/api/v1/watch/chat-filter/{id}': {
      get: {
        tags: ['中控台'],
        summary: '聊天审核页 URL',
        operationId: 'getChatFilterPage',
        parameters: [
          watchId,
          {
            name: 'email',
            in: 'query',
            schema: { type: 'string' },
            description: '默认 assistant@zhibo.local',
          },
          {
            name: 'name',
            in: 'query',
            schema: { type: 'string', maxLength: 30 },
            description: '默认「助理」',
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseChatFilter'),
      },
    },
  }

  finalizeSchemas(schemas)

  return {
    openapi: '3.0.3',
    info: {
      title: 'zhibo live-service',
      description: [
        '钱坤直播观看端 / 中控台 API（live-service :8082）。',
        '',
        '契约来源：`zhibo-shared/docs/API约定.md`（D58/D61）。',
        '本文件供 Apifox 导入，也是正式后端按文档反向实现的权威输入之一。',
        '',
        '**通则**',
        '- 成功：`code === 0`（HTTP 仍多为 200）',
        '- 活动路径 `{id}` 均为本站 **UUID**',
        '- 时间：`yyyy-MM-dd HH:mm:ss`',
        '- JSON 不输出 `null` 字段',
        '- 观看端不走管理端 JWT',
      ].join('\n'),
      version: '2.5.0',
      contact: { name: 'zhibo-backend' },
    },
    servers: [
      { url: 'http://127.0.0.1:8082', description: '本地 live-service' },
    ],
    tags: [
      { name: '观看' },
      { name: '进出房' },
      { name: '中控台' },
      { name: '回调' },
    ],
    paths,
    components: {
      parameters: {
        WatchActivityId: {
          name: 'id',
          in: 'path',
          required: true,
          schema: {
            type: 'string',
            format: 'uuid',
            example: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
          },
          description: '本站活动 UUID',
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
      version: '2.5.0',
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

const admin = buildAdmin()
const live = buildLive()
attachApifoxEnums(admin)
attachApifoxEnums(live)
const all = mergeDocs(admin, live)

writeDoc('admin-service', admin)
writeDoc('live-service', live)
writeDoc('zhibo', all)

console.log('\nApifox 导入建议：优先导入 zhibo.openapi.yaml（或 .json）一次搞定。')
