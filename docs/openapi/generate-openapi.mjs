/**
 * 按 zhibo-shared/docs/API约定.md 生成 OpenAPI 3.0.3
 * 用法：node generate-openapi.mjs
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
    description: '从 1 起（D37）',
  },
  {
    name: 'pageSize',
    in: 'query',
    schema: { type: 'integer', default: 20, minimum: 1, maximum: 100 },
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
          data: dataSchema,
        },
      },
    ],
  }
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
      description: '直播服务商；创建后不可改',
    },
    ActivityType: {
      type: 'integer',
      enum: [1, 2, 3, 4],
      description: '1音频 / 2视频 / 3互动 / 4录播',
    },
    ActivityState: {
      type: 'integer',
      enum: [0, 1, 2, 3, 4, 5],
      description: '0全部(列表筛) / 1直播 / 2预告 / 3结束 / 4点播 / 5回放',
    },
    ViewerStyle: {
      type: 'string',
      enum: ['traditional', 'immersive'],
    },
    WarmupType: { type: 'string', enum: ['image', 'video'] },
    ShelfStatus: {
      type: 'string',
      enum: ['pending', 'on', 'off', 'banned'],
    },
    RiskStatus: {
      type: 'string',
      enum: ['pending', 'passed', 'rejected'],
    },
    SellType: {
      type: 'string',
      enum: ['free', 'paid', 'encrypted', 'specified'],
    },
    AccessValidityType: {
      type: 'string',
      enum: ['long', 'days'],
    },
    PlaybackValidityType: {
      type: 'string',
      enum: ['forever', 'days'],
    },
    MaterialType: {
      type: 'string',
      enum: ['image', 'video', 'document', 'article'],
    },
    MaterialFileType: {
      type: 'string',
      enum: ['image', 'video', 'document'],
      description: '批量登记仅接受文件类型，不含 article',
    },
    AuditWriteStatus: {
      type: 'string',
      enum: ['passed', 'rejected'],
    },
    MaterialAuditStatus: {
      type: 'string',
      enum: ['pending', 'passed', 'rejected'],
    },
    IdData: {
      type: 'object',
      required: ['id'],
      properties: { id: { type: 'string', format: 'uuid' } },
    },
    EmployeeBrief: {
      type: 'object',
      properties: {
        id: { type: 'string', description: 'rs_mainkey_id 十进制字符串' },
        userName: { type: 'string' },
        avatar: { type: 'string' },
        userAccount: { type: 'string' },
      },
    },
    ProductRef: {
      type: 'object',
      required: ['spuId', 'productName'],
      properties: {
        spuId: { type: 'string', description: '十进制字符串' },
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
          description: '0 pending / 1 success / 2 failed / 3 expired',
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
        uploaderId: { type: 'string' },
        uploaderName: { type: 'string' },
        departmentName: { type: 'string' },
        uploadedAt: { type: 'string', example: '2026-10-12 14:00:00' },
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
    ArticleDetail: {
      type: 'object',
      properties: {
        id: { type: 'string', format: 'uuid' },
        groupId: { type: 'string', format: 'uuid' },
        title: { type: 'string' },
        author: { type: 'string' },
        content: { type: 'string' },
        aiAuditStatus: { $ref: '#/components/schemas/MaterialAuditStatus' },
        riskAuditStatus: { $ref: '#/components/schemas/MaterialAuditStatus' },
      },
    },
    CreateActivityRequest: {
      type: 'object',
      required: ['provider', 'title', 'startTime', 'type', 'autoList', 'hostIds'],
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
          type: 'string',
          description: 'type=4 且 provider=vhall 时必填',
        },
        introduction: { type: 'string', maxLength: 256 },
        viewerStyle: { $ref: '#/components/schemas/ViewerStyle' },
        ultraLowLatency: {
          type: 'boolean',
          description: '创建后不可改',
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
          items: { type: 'string' },
          description: '主播员工 id',
        },
        ownerIds: {
          type: 'array',
          maxItems: 20,
          items: { type: 'string' },
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
          items: { type: 'string' },
        },
        ownerIds: {
          type: 'array',
          maxItems: 20,
          items: { type: 'string' },
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
        price: { description: 'paid 时必填；元，最多 2 位小数' },
        linePrice: { description: '须 > price；非 paid 忽略' },
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
        result: { type: 'string', enum: ['passed', 'rejected'] },
        reason: { type: 'string' },
        operatorId: { type: 'string' },
        operatorName: { type: 'string' },
        createdAt: { type: 'string' },
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
        shareLink: { type: 'string' },
        createdAt: { type: 'string' },
        shelfStatus: { $ref: '#/components/schemas/ShelfStatus' },
        riskStatus: { $ref: '#/components/schemas/RiskStatus' },
        sellType: { $ref: '#/components/schemas/SellType' },
        validityLabel: { type: 'string' },
        canBroadcast: { type: 'boolean' },
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
            vhallWebinarId: {
              type: 'string',
              description: '兼容字段；仅 vhall 时等于 providerRoomId',
            },
            introduction: { type: 'string' },
            endTime: { type: 'string' },
            verify: { type: 'integer' },
            pv: { type: 'integer' },
            fullEmbedShareLink: { type: 'string' },
            ultraLowLatency: { type: 'boolean' },
            detailHtml: { type: 'string' },
            autoList: { type: 'boolean' },
            hosts: {
              type: 'array',
              items: { $ref: '#/components/schemas/EmployeeBrief' },
            },
            coursewares: {
              type: 'array',
              items: { $ref: '#/components/schemas/CoursewareItem' },
            },
            sellAlone: { type: 'boolean' },
            price: {},
            linePrice: {},
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
        id: { type: 'string', description: 'create-demand 的 record_id' },
        title: { type: 'string' },
        duration: { type: 'string', example: '01:02:03' },
        createdAt: { type: 'string' },
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
    EndActivityData: {
      type: 'object',
      required: ['id'],
      properties: {
        id: { type: 'string', format: 'uuid' },
        state: { type: 'integer', example: 3 },
      },
    },
    HostData: {
      type: 'object',
      required: ['provider', 'hostUrl'],
      properties: {
        provider: { $ref: '#/components/schemas/Provider' },
        hostUrl: { type: 'string', format: 'uri' },
      },
    },
    EmbedData: {
      type: 'object',
      required: ['embedUrl'],
      properties: { embedUrl: { type: 'string', format: 'uri' } },
    },
    ActivityStats: {
      type: 'object',
      properties: {
        id: { type: 'string', format: 'uuid' },
        title: { type: 'string' },
        state: { type: 'integer' },
        type: { type: 'integer' },
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
        activityState: { type: 'integer' },
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
        id: { type: 'string' },
        name: { type: 'string' },
        code: { type: 'string' },
        parentId: { type: 'string' },
        children: {
          type: 'array',
          items: { $ref: '#/components/schemas/OrgDeptNode' },
        },
      },
    },
    OrgEmployeeItem: {
      type: 'object',
      properties: {
        id: { type: 'string' },
        userName: { type: 'string' },
        departmentName: { type: 'string' },
        roleNames: { type: 'string' },
        liveEnabled: { type: 'boolean' },
        hqStatus: {
          type: 'string',
          enum: ['normal', 'disabled', 'unknown'],
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
        id: { type: 'string' },
        liveEnabled: { type: 'boolean' },
      },
    },
    OrgRoleBrief: {
      type: 'object',
      properties: {
        id: { type: 'string', description: 'sys_uimsrole.rs_mainkey_id' },
        name: { type: 'string' },
      },
    },
    OrgRoleDetail: {
      type: 'object',
      properties: {
        id: { type: 'string' },
        name: { type: 'string' },
        remark: { type: 'string' },
        members: {
          type: 'array',
          items: {
            type: 'object',
            properties: {
              id: { type: 'string' },
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
        id: { type: 'integer', format: 'int64', description: '角色 rs_mainkey_id' },
        menuCodes: {
          type: 'array',
          items: { type: 'string' },
          example: ['live-manage'],
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
    ApiResponseEndActivity: { $ref: '#/components/schemas/EndActivityData' },
    ApiResponseHost: { $ref: '#/components/schemas/HostData' },
    ApiResponseEmbed: { $ref: '#/components/schemas/EmbedData' },
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
  })) {
    schemas[name] = wrapData(data)
  }

  const activityId = { $ref: '#/components/parameters/ActivityId' }
  const materialId = { $ref: '#/components/parameters/MaterialId' }

  const paths = {
    '/api/auth/captcha': {
      get: {
        tags: ['登录鉴权'],
        summary: '图形验证码',
        operationId: 'getCaptcha',
        security: [],
        responses: ok200('#/components/schemas/ApiResponseCaptcha'),
      },
    },
    '/api/auth/login': {
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
    '/api/auth/wechat/callback': {
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
    '/api/auth/login/query': {
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
    '/api/auth/refresh': {
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
    '/api/auth/me': {
      get: {
        tags: ['登录鉴权'],
        summary: '当前员工',
        operationId: 'getMe',
        security: bearer,
        responses: ok200('#/components/schemas/ApiResponseMe'),
      },
    },
    '/api/uploads/image': {
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
    '/api/uploads/tos-sts': {
      get: {
        tags: ['图片上传'],
        summary: '火山 TOS 临时上传凭证',
        operationId: 'getTosSts',
        security: bearer,
        responses: ok200('#/components/schemas/ApiResponseTosSts'),
      },
    },
    '/api/materials/groups': {
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
    '/api/materials/groups/{id}': {
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
      delete: {
        tags: ['素材中心'],
        summary: '删除分组',
        operationId: 'deleteMaterialGroup',
        security: bearer,
        parameters: [materialId],
        responses: ok200('#/components/schemas/ApiResponseId'),
      },
    },
    '/api/materials': {
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
            name: 'selectableOnly',
            in: 'query',
            schema: { type: 'boolean', default: false },
            description: 'true 时仅两审均为 passed',
          },
          ...pageParams,
        ],
        responses: ok200('#/components/schemas/ApiResponseMaterialList'),
      },
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
    '/api/materials/articles': {
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
    '/api/materials/articles/{id}': {
      get: {
        tags: ['素材中心'],
        summary: '文章详情',
        operationId: 'getArticle',
        security: bearer,
        parameters: [materialId],
        responses: ok200('#/components/schemas/ApiResponseArticleDetail'),
      },
      put: {
        tags: ['素材中心'],
        summary: '编辑文章',
        description: '待审核不可编辑；成功后两审回 pending',
        operationId: 'updateArticle',
        security: bearer,
        parameters: [materialId],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                required: ['title', 'author', 'content'],
                properties: {
                  title: { type: 'string', maxLength: 45 },
                  author: { type: 'string', maxLength: 16 },
                  content: { type: 'string', maxLength: 100000 },
                },
              },
            },
          },
        },
        responses: ok200('#/components/schemas/ApiResponseMaterialItem'),
      },
    },
    '/api/materials/{id}': {
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
      delete: {
        tags: ['素材中心'],
        summary: '软删素材',
        operationId: 'deleteMaterial',
        security: bearer,
        parameters: [materialId],
        responses: ok200('#/components/schemas/ApiResponseId'),
      },
    },
    '/api/materials/{id}/group': {
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
    '/api/materials/{id}/image': {
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
    '/api/materials/{id}/ai-audit': {
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
    '/api/materials/{id}/risk-audit': {
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
    '/api/activities': {
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
                    hostIds: ['10001'],
                    ownerIds: ['10001'],
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
    '/api/activities/{id}': {
      get: {
        tags: ['活动管理'],
        summary: '活动详情',
        operationId: 'getActivity',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseActivityDetail'),
      },
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
      delete: {
        tags: ['活动管理'],
        summary: '软删活动',
        operationId: 'deleteActivity',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseId'),
      },
    },
    '/api/activities/{id}/playback': {
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
    '/api/activities/{id}/sales': {
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
    '/api/activities/{id}/end': {
      post: {
        tags: ['活动管理'],
        summary: '结束直播',
        operationId: 'endActivity',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseEndActivity'),
      },
    },
    '/api/activities/{id}/shelf': {
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
    '/api/activities/{id}/audits': {
      get: {
        tags: ['活动管理'],
        summary: '审核记录',
        operationId: 'listActivityAudits',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseAudits'),
      },
    },
    '/api/activities/{id}/audit': {
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
    '/api/activities/{id}/records': {
      get: {
        tags: ['活动管理'],
        summary: '某场活动的回放列表',
        operationId: 'listActivityRecords',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseRecordList'),
      },
    },
    '/api/activities/{id}/host': {
      get: {
        tags: ['活动管理'],
        summary: '主持人开播链接',
        description: '管理端拿到 hostUrl 后新窗口打开，不要 iframe。canBroadcast 不为真 → 400。',
        operationId: 'getActivityHost',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseHost'),
      },
    },
    '/api/activities/{id}/embed': {
      get: {
        tags: ['活动管理'],
        summary: '管理后台嵌入页',
        operationId: 'getActivityEmbed',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseEmbed'),
      },
    },
    '/api/activities/{id}/stats': {
      get: {
        tags: ['统计'],
        summary: '观看概览',
        operationId: 'getActivityStats',
        security: bearer,
        parameters: [activityId],
        responses: ok200('#/components/schemas/ApiResponseStats'),
      },
    },
    '/api/activities/{id}/stats/online': {
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
    '/api/activities/{id}/presence-trend': {
      get: {
        tags: ['统计'],
        summary: '进出三线走势（管理端）',
        description: '与 live GET /api/watch/{id}/presence-trend 同形。',
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
            schema: { type: 'integer', default: 60, enum: [60] },
          },
        ],
        responses: ok200('#/components/schemas/ApiResponsePresenceTrend'),
      },
    },
    '/api/activities/{id}/attendees': {
      get: {
        tags: ['中控台'],
        summary: '中控台进出名单（废弃）',
        description:
          '废弃：新代码一律用 live GET /api/watch/{id}/attendees。实现可暂时保留。',
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
            },
          },
          ...pageParams,
          { name: 'keyword', in: 'query', schema: { type: 'string' } },
        ],
        responses: ok200('#/components/schemas/ApiResponseAttendeeList'),
      },
    },
    '/api/employees': {
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
    '/api/org/departments/tree': {
      get: {
        tags: ['员工组织'],
        summary: '部门树',
        operationId: 'getOrgDepartmentTree',
        security: bearer,
        responses: ok200('#/components/schemas/ApiResponseOrgDeptTree'),
      },
    },
    '/api/org/employees': {
      get: {
        tags: ['员工组织'],
        summary: '部门下员工',
        operationId: 'listOrgEmployees',
        security: bearer,
        parameters: [
          {
            name: 'deptId',
            in: 'query',
            schema: { type: 'string' },
            description: '不传=全部；有值不含下级',
          },
          { name: 'userName', in: 'query', schema: { type: 'string' } },
          { name: 'roleId', in: 'query', schema: { type: 'string' } },
          ...pageParams,
        ],
        responses: ok200('#/components/schemas/ApiResponseOrgEmployees'),
      },
    },
    '/api/org/employees/{id}/live-enabled': {
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
            schema: { type: 'string' },
            description: '账号 rs_mainkey_id',
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
    '/api/roles/list': {
      get: {
        tags: ['员工组织'],
        summary: '在用角色列表',
        operationId: 'listRoles',
        security: bearer,
        responses: ok200('#/components/schemas/ApiResponseOrgRoles'),
      },
    },
    '/api/roles/detail/{id}': {
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
    '/api/roles/save': {
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
  }

  return {
    openapi: '3.0.3',
    info: {
      title: 'zhibo admin-service',
      description: [
        '钱坤直播管理端 API（admin-service :8081）。',
        '',
        '契约来源：`zhibo-shared/docs/API约定.md`。',
        '',
        '**通则**',
        '- 成功：`code === 0`（HTTP 仍多为 200）',
        '- 失败：业务 `code` —— `400` 参数、`401` 未登录、`403` 停用、`500` 服务器、`501` 服务商未接入、云侧失败透传或 `502`',
        '- 活动路径 `{id}` 均为本站 **UUID**',
        '- 时间：`yyyy-MM-dd HH:mm:ss`',
        '- JSON 不输出 `null` 字段',
        '- 除登录白名单外需 `Authorization: Bearer <accessToken>`',
      ].join('\n'),
      version: '2.0.0',
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
        state: { type: 'integer' },
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
        state: { type: 'integer' },
        type: { type: 'integer' },
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
        state: { type: 'integer' },
        type: { type: 'integer' },
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
        activityState: { type: 'integer' },
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
    '/api/watch/activities': {
      get: {
        tags: ['观看'],
        summary: '活动列表（精简）',
        operationId: 'listWatchActivities',
        parameters: [
          ...pageParams,
          {
            name: 'state',
            in: 'query',
            schema: { type: 'integer' },
          },
        ],
        responses: ok200('#/components/schemas/ApiResponseWatchList'),
      },
    },
    '/api/watch/{id}': {
      get: {
        tags: ['观看'],
        summary: '观看嵌入（iframe 兜底）',
        operationId: 'getWatchEmbed',
        parameters: [watchId, ...guestParams],
        responses: ok200('#/components/schemas/ApiResponseWatchEmbed'),
      },
    },
    '/api/watch/{id}/sdk': {
      get: {
        tags: ['观看'],
        summary: '学生端观看 SDK 初始化载荷',
        operationId: 'getWatchSdk',
        parameters: [watchId, ...guestParams],
        responses: ok200('#/components/schemas/ApiResponseWatchSdk'),
      },
    },
    '/api/watch/{id}/attendance/enter': {
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
    '/api/watch/{id}/attendance/leave': {
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
    '/api/callbacks/vhall/presence': {
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
    '/api/watch/{id}/attendees': {
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
            },
          },
          ...pageParams,
          { name: 'keyword', in: 'query', schema: { type: 'string' } },
        ],
        responses: ok200('#/components/schemas/ApiResponseAttendeeList'),
      },
    },
    '/api/watch/{id}/presence-trend': {
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
            schema: { type: 'integer', default: 60, enum: [60] },
          },
        ],
        responses: ok200('#/components/schemas/ApiResponsePresenceTrend'),
      },
    },
    '/api/watch/{id}/chat-filter': {
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

  return {
    openapi: '3.0.3',
    info: {
      title: 'zhibo live-service',
      description: [
        '钱坤直播观看端 / 中控台 API（live-service :8082）。',
        '',
        '契约来源：`zhibo-shared/docs/API约定.md`。',
        '',
        '**通则**',
        '- 成功：`code === 0`（HTTP 仍多为 200）',
        '- 活动路径 `{id}` 均为本站 **UUID**',
        '- 时间：`yyyy-MM-dd HH:mm:ss`',
        '- JSON 不输出 `null` 字段',
        '- 观看端不走管理端 JWT',
      ].join('\n'),
      version: '2.0.0',
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
        '契约：`zhibo-shared/docs/API约定.md`。',
        '分服务文件：`admin-service.openapi.yaml` / `live-service.openapi.yaml`。',
        '',
        '导入后请在 Apifox 按 Tag 前缀区分服务；调试时切换对应 Server。',
      ].join('\n'),
      version: '2.0.0',
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
const all = mergeDocs(admin, live)

writeDoc('admin-service', admin)
writeDoc('live-service', live)
writeDoc('zhibo', all)

console.log('\nApifox 导入建议：优先导入 zhibo.openapi.yaml（或 .json）一次搞定。')
