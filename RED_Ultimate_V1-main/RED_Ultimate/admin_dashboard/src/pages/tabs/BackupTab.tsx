import React from 'react';
import { Alert, Card, Typography, Space, Button, message } from 'antd';
import { CopyOutlined, CloudDownloadOutlined, SafetyOutlined } from '@ant-design/icons';

export default function BackupTab() {
  const copy = (text: string) => {
    navigator.clipboard.writeText(text).then(()=>message.success('تم النسخ')).catch(()=>message.error('فشل النسخ'));
  };
  const commands = [
    { title: '1. مفاتيح سلطة الهوية (الأهم)', cmd: 'tar -czf younes-identity-$(date +%F).tar.gz RED_Ultimate/secrets/ && gpg -c younes-identity-*.tar.gz', desc: 'يحتوي المفتاح الخاص P-256 — لا ترفعه إلى Git أبدًا' },
    { title: '2. PostgreSQL', cmd: 'docker exec red-db-sql pg_dump -U admin red_sovereign | gzip > pg-$(date +%F).sql.gz', desc: 'الحسابات والأجهزة والشهادات و recovery codes' },
    { title: '3. MongoDB', cmd: 'docker exec red-db-nosql mongodump --username red_user --password $MONGO_PASSWORD --authenticationDatabase admin --out /tmp/mongodump && docker cp red-db-nosql:/tmp/mongodump ./mongodump-$(date +%F)', desc: 'الرسائل المشفرة والقصص والمنشورات' },
    { title: '4. Redis (AOF)', cmd: 'docker exec red-cache redis-cli -a $REDIS_PASSWORD --rdb /data/dump.rdb && docker cp red-cache:/data/dump.rdb ./redis-$(date +%F).rdb', desc: 'عدادات PSTN اليومية و rate limits' },
    { title: '5. MinIO', cmd: 'mc mirror --overwrite minio/red-media ./minio-backup-$(date +%F)  # أو: docker exec red-storage tar -czf - /data | gzip > minio-$(date +%F).tar.gz', desc: 'صور وفيديو ومرفقات مشفرة' },
    { title: 'الاستعادة — اختبرها', cmd: 'docker compose down -v && docker compose up -d && # ثم استعد كل ملف بالعكس + تحقق من تسجيل دخول مستخدم', desc: 'لا يعتبر النسخ صالحًا قبل restore drill على بيئة منفصلة' },
  ];
  return <Space direction="vertical" style={{width:'100%'}} size={16}>
    <Alert type="warning" showIcon message="النسخ الاحتياطي السيادي" description="النسخة الكاملة = 5 أجزاء معًا. فقدان أي جزء = فقدان حسابات أو رسائل أو وسائط. خزّنها مشفرة في مكانين منفصلين." />
    {commands.map(item => <Card key={item.title} title={item.title} extra={<Button icon={<CopyOutlined/>} onClick={()=>copy(item.cmd)}>نسخ</Button>}><Typography.Paragraph type="secondary">{item.desc}</Typography.Paragraph><Typography.Text code copyable style={{display:'block', whiteSpace:'pre-wrap', background:'#0a0a0a', padding:12, borderRadius:8}}>{item.cmd}</Typography.Text></Card>)}
    <Card title="◆ قائمة التحقق قبل الإنتاج" extra={<SafetyOutlined style={{color:'#00C896'}}/>}><Typography.Text>• هل جربت الاستعادة على جهاز آخر؟ • هل تحققت من تسجيل دخول + فك تشفير رسالة + عرض صورة؟ • هل المفاتيح في خزنة offline؟</Typography.Text></Card>
    <Alert type="info" showIcon message="تذكير" description="لا ترفع .env أو secrets/ أو *.pem أو dump إلى Git — هي في .gitignore لسبب." />
  </Space>;
}
