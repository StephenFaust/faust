/**
 * 工作空间服务层：只做「请求 + 解包」，字段名与后端保持一致。
 */
import { get, post } from '@/lib/http';

export interface SpaceInfo {
  spaceId: number;
  name: string;
  /** PERSONAL / TEAM */
  spaceType: string;
  spaceTypeDesc?: string;
  ownerUcId?: number;
  ownerName?: string;
  description?: string;
  /** 当前用户在该空间的角色：OWNER / MEMBER */
  role?: string;
  owner?: boolean;
  personal?: boolean;
  memberCount?: number;
  agentCount?: number;
  toolCount?: number;
  skillCount?: number;
  dataSourceCount?: number;
  createTime?: string;
}

export interface SpaceMember {
  id: number;
  spaceId: number;
  ucId: number;
  ucName?: string;
  role: string;
  roleDesc?: string;
  owner?: boolean;
  createTime?: string;
}

export function fetchSpaceList() {
  return get<SpaceInfo[]>('/api/space/list');
}

export function fetchSpaceDetail(spaceId: number) {
  return get<SpaceInfo>('/api/space/detail', { spaceId });
}

export function createSpace(body: { name: string; description?: string }) {
  return post<SpaceInfo>('/api/space/create', body);
}

export function renameSpace(body: { spaceId: number; name: string }) {
  return post<boolean>('/api/space/rename', body);
}

export function dissolveSpace(spaceId: number) {
  return post<boolean>('/api/space/dissolve', { spaceId });
}

export function fetchSpaceMembers(spaceId: number) {
  return get<SpaceMember[]>('/api/space/member/list', { spaceId });
}

export function addSpaceMember(body: { spaceId: number; ucId: number; ucName?: string }) {
  return post<boolean>('/api/space/member/add', body);
}

export function removeSpaceMember(body: { spaceId: number; ucId: number }) {
  return post<boolean>('/api/space/member/remove', body);
}

export function leaveSpace(spaceId: number) {
  return post<boolean>('/api/space/member/leave', { spaceId });
}
