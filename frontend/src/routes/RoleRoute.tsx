import { Navigate, Outlet } from 'react-router-dom';
import { useAuthStore } from '@stores/auth.store';
import { useUIStore } from '@stores/ui.store';
import { PERMISSION_TOAST_ID } from '@api/client';
import type { Role } from '@api/types';
import { ROUTES } from '@utils/constants';

interface RoleRouteProps {
  allowedRoles: Role[];
}

export default function RoleRoute({ allowedRoles }: RoleRouteProps) {
  const { isAuthenticated, role } = useAuthStore();
  const addToast = useUIStore((s) => s.addToast);

  if (!isAuthenticated) {
    return <Navigate to={ROUTES.LOGIN} replace />;
  }

  if (!role || !allowedRoles.includes(role)) {
    addToast({ id: PERMISSION_TOAST_ID, message: 'You do not have permission to access this page', variant: 'error' });
    return <Navigate to={ROUTES.HOME} replace />;
  }

  return <Outlet />;
}
