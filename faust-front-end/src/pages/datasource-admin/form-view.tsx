import * as React from 'react';
import { ArrowLeft, CheckCircle2, Database, Loader2, PlugZap, Save, XCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field, FieldGroup } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { PageHeader } from '@/components/layout/page-header';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/controls';
import {
  createDataSource,
  fetchDataSourceDetail,
  testDataSource,
  updateDataSource,
  type DataSourceDetail
} from '@/services/agent-center';
import { useAsyncData } from '@/lib/hooks/use-paged-list';
import { DB_TYPES } from '@/lib/constants';
import { toast } from 'sonner';

interface FormState {
  dbType: string;
  dbName: string;
  url: string;
  user: string;
  pwd: string;
}

const BLANK: FormState = { dbType: '', dbName: '', url: '', user: '', pwd: '' };

type TestStatus = 'idle' | 'loading' | 'success' | 'fail';

export function DataSourceFormView({
  id,
  onBack,
  onSaved
}: {
  id?: number;
  onBack: () => void;
  onSaved: () => void;
}) {
  const isEdit = typeof id === 'number';
  const [form, setForm] = React.useState<FormState>(BLANK);
  const [errors, setErrors] = React.useState<Partial<Record<keyof FormState, string>>>({});
  const [saving, setSaving] = React.useState(false);
  const [testStatus, setTestStatus] = React.useState<TestStatus>('idle');
  const [testReason, setTestReason] = React.useState('');
  const [populated, setPopulated] = React.useState(!isEdit);

  const detail = useAsyncData<DataSourceDetail>(
    isEdit ? () => fetchDataSourceDetail(id as number) : null,
    [id]
  );

  React.useEffect(() => {
    const data = detail.data;
    if (!isEdit || !data || populated) return;
    setForm({
      dbType: data.dbType || '',
      dbName: data.dbName || '',
      url: data.url || '',
      user: data.user || '',
      pwd: ''
    });
    setPopulated(true);
  }, [detail.data, isEdit, populated]);

  // 表单版本号：用于丢弃「参数已改、旧结论后到」的连接测试响应
  const versionRef = React.useRef(0);

  const setField = (key: keyof FormState, value: string) => {
    versionRef.current += 1;
    setForm((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
    setTestStatus('idle');
    setTestReason('');
  };

  const trimmedPwd = form.pwd.trim();
  const canTest = Boolean(form.dbType && form.url.trim() && form.user.trim() && trimmedPwd);
  // 编辑态留空密码 = 保留原值，后端不会重新连接，因此无需再次测试
  const needsTest = !(isEdit && !trimmedPwd);

  const validate = () => {
    const next: Partial<Record<keyof FormState, string>> = {};
    if (!form.dbType) next.dbType = '请选择数据库类型';
    if (!form.dbName.trim()) next.dbName = '请填写数据库名称';
    if (!form.url.trim()) next.url = '请填写连接地址';
    if (!form.user.trim()) next.user = '请填写用户名';
    if (!trimmedPwd && !isEdit) next.pwd = '请填写密码';
    setErrors(next);
    if (Object.keys(next).length) {
      toast.error('请完善必填项后再保存');
      return false;
    }
    return true;
  };

  const runTest = async () => {
    if (!canTest) {
      toast.error('请先填写数据库类型、连接地址、用户名和密码');
      return;
    }
    const version = versionRef.current;
    setTestStatus('loading');
    setTestReason('');
    const result = await testDataSource({
      dbType: form.dbType,
      url: form.url.trim(),
      user: form.user.trim(),
      pwd: trimmedPwd
    });
    if (version !== versionRef.current) return;
    if (result.ok) {
      setTestStatus('success');
    } else {
      setTestStatus('fail');
      setTestReason(result.reason);
    }
  };

  const submit = async () => {
    if (!populated) {
      toast.error('详情尚未加载完成，请稍后重试');
      return;
    }
    if (!validate()) return;
    if (needsTest && testStatus !== 'success') {
      toast.error('请先通过连接测试后再保存');
      return;
    }
    setSaving(true);
    try {
      const body: Record<string, unknown> = {
        dbType: form.dbType,
        dbName: form.dbName.trim(),
        url: form.url.trim(),
        user: form.user.trim()
      };
      if (trimmedPwd) body.pwd = trimmedPwd;
      if (isEdit) {
        body.id = id;
        await updateDataSource(body);
        toast.success('数据源已更新');
      } else {
        await createDataSource(body);
        toast.success('数据源已创建');
      }
      onSaved();
    } catch (error) {
      toast.error(`${isEdit ? '更新' : '创建'}失败：${error instanceof Error ? error.message : '未知错误'}`);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="animate-fade-in">
      <PageHeader
        title={isEdit ? '编辑数据源' : '新增数据源'}
        crumbs={[
          { text: 'Faust', to: '/' },
          { text: '数据源', to: '/datasource' },
          { text: isEdit ? '编辑' : '新增' }
        ]}
        actions={
          <Button variant="outline" onClick={onBack}>
            <ArrowLeft className="h-4 w-4" />
            返回列表
          </Button>
        }
      />

      {!populated ? (
        <div className="rounded-2xl border border-border/70 bg-card p-5 shadow-panel">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {Array.from({ length: 5 }).map((_, index) => (
              <Skeleton key={index} className="h-16" />
            ))}
          </div>
        </div>
      ) : (
        <>
          <FieldGroup
            title={
              <>
                <Database className="h-4 w-4 text-primary" />
                连接配置
              </>
            }
          >
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field label="数据库类型" required error={errors.dbType}>
                <Select
                  value={form.dbType || undefined}
                  onChange={(value) => setField('dbType', value ? String(value) : '')}
                  options={DB_TYPES}
                  placeholder="请选择数据库类型"
                />
              </Field>
              <Field label="数据库名称" required error={errors.dbName}>
                <Input
                  value={form.dbName}
                  onChange={(event) => setField('dbName', event.target.value)}
                  placeholder="如 test_db"
                />
              </Field>
              <Field label="用户名" required error={errors.user}>
                <Input
                  value={form.user}
                  onChange={(event) => setField('user', event.target.value)}
                  placeholder="连接用户名"
                />
              </Field>
              <Field
                label={isEdit ? '密码（留空保留原值）' : '密码'}
                required={!isEdit}
                error={errors.pwd}
              >
                <Input
                  type="password"
                  value={form.pwd}
                  onChange={(event) => setField('pwd', event.target.value)}
                  placeholder={isEdit ? '不修改请留空' : '连接密码'}
                  autoComplete="new-password"
                />
              </Field>
              <Field label="连接地址" required error={errors.url} className="sm:col-span-2">
                <Input
                  value={form.url}
                  onChange={(event) => setField('url', event.target.value)}
                  placeholder="jdbc:mysql://host:3306/dbname"
                  className="font-mono text-[12.5px]"
                />
              </Field>
            </div>

            <div className="flex flex-wrap items-center gap-2.5 border-t border-border/60 pt-4">
              <Button variant="outline" size="sm" onClick={runTest} disabled={testStatus === 'loading' || !canTest}>
                {testStatus === 'loading' ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                ) : (
                  <PlugZap className="h-3.5 w-3.5" />
                )}
                连接测试
              </Button>

              {testStatus === 'success' ? (
                <Badge tone="success">
                  <CheckCircle2 className="h-3 w-3" />
                  连接成功
                </Badge>
              ) : null}
              {testStatus === 'fail' ? (
                <Badge tone="danger">
                  <XCircle className="h-3 w-3" />
                  连接失败
                </Badge>
              ) : null}

              {!needsTest ? (
                <span className="text-[11.5px] text-muted-foreground">
                  编辑模式且未修改密码，无需重新测试
                </span>
              ) : testStatus === 'idle' ? (
                <span className="text-[11.5px] text-muted-foreground">
                  填写完整连接信息后点击测试，通过后才能保存
                </span>
              ) : null}
            </div>

            {testStatus === 'fail' && testReason ? (
              <p className="rounded-lg bg-rose-500/8 px-3 py-2 text-[12px] leading-relaxed text-rose-700">
                失败原因：{testReason}
              </p>
            ) : null}
          </FieldGroup>

          <div className="mt-4 flex items-center justify-end gap-2">
            <Button variant="outline" onClick={onBack}>
              取消
            </Button>
            <Button onClick={submit} disabled={saving || (needsTest && testStatus !== 'success')}>
              {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
              {saving ? '保存中…' : '保存'}
            </Button>
          </div>
        </>
      )}
    </div>
  );
}
