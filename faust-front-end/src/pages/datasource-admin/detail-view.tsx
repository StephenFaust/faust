import * as React from 'react';
import { ArrowLeft, Database, Info } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { PageHeader, SectionCard } from '@/components/layout/page-header';
import { Descriptions } from '@/components/descriptions';
import { Skeleton } from '@/components/ui/controls';
import { EmptyState } from '@/components/empty-state';
import { fetchDataSourceDetail, type DataSourceDetail } from '@/services/agent-center';
import { useAsyncData } from '@/lib/hooks/use-paged-list';
import { DB_TYPES } from '@/lib/constants';
import { formatDateTime } from '@/lib/format';

function dbTypeLabel(type?: string) {
  return DB_TYPES.find((item) => item.value === type)?.label || type || '—';
}

export function DataSourceDetailView({ id, onBack }: { id: number; onBack: () => void }) {
  const { data, loading, error, reload } = useAsyncData<DataSourceDetail>(
    () => fetchDataSourceDetail(id),
    [id]
  );

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="数据源详情"
        crumbs={[
          { text: 'Faust', to: '/' },
          { text: '数据源', to: '/datasource' },
          { text: '详情' }
        ]}
        actions={
          <Button variant="outline" onClick={onBack}>
            <ArrowLeft className="h-4 w-4" />
            返回列表
          </Button>
        }
      />

      {loading ? (
        <SectionCard title="基本信息">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {Array.from({ length: 6 }).map((_, index) => (
              <Skeleton key={index} className="h-10" />
            ))}
          </div>
        </SectionCard>
      ) : error ? (
        <EmptyState
          title="加载失败"
          description={error}
          action={
            <Button variant="outline" size="sm" onClick={reload}>
              重试
            </Button>
          }
        />
      ) : data ? (
        <div className="flex flex-col gap-4">
          <SectionCard
            title={
              <>
                <Info className="h-4 w-4 text-primary" />
                基本信息
              </>
            }
          >
            <Descriptions
              columns={2}
              items={[
                { label: '数据库名称', value: data.dbName || '—' },
                { label: '数据库类型', value: dbTypeLabel(data.dbType) },
                { label: '用户名', value: data.user || '—' },
                { label: '创建时间', value: formatDateTime(data.createTime) },
                { label: '创建人', value: data.createBy ?? '—' },
                { label: '更新时间', value: formatDateTime(data.updateTime) },
                { label: '更新人', value: data.updateBy ?? '—' }
              ]}
            />
          </SectionCard>

          <SectionCard
            title={
              <>
                <Database className="h-4 w-4 text-primary" />
                连接信息
              </>
            }
          >
            <Descriptions
              columns={2}
              items={[{ label: '连接地址', value: data.url || '—', span: 2 }]}
            />
            <p className="mt-3 text-[11.5px] leading-relaxed text-muted-foreground">
              出于安全考虑，详情接口不返回数据库密码；需要修改密码时在编辑页填写新值即可。
            </p>
          </SectionCard>
        </div>
      ) : (
        <EmptyState title="未找到数据源" description="该数据源可能已被删除。" />
      )}
    </div>
  );
}
