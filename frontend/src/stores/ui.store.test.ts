import { describe, it, expect, beforeEach } from 'vitest';
import { useUIStore } from './ui.store';

describe('useUIStore — addToast deduplication (BUG-009)', () => {
  beforeEach(() => {
    useUIStore.setState({ toastQueue: [] });
  });

  it('should append a new toast with auto-generated ID when no ID is provided', () => {
    const id1 = useUIStore.getState().addToast({ message: 'First', variant: 'success' });
    const id2 = useUIStore.getState().addToast({ message: 'Second', variant: 'info' });

    const { toastQueue } = useUIStore.getState();
    expect(toastQueue).toHaveLength(2);
    expect(toastQueue[0].id).toBe(id1);
    expect(toastQueue[1].id).toBe(id2);
    expect(id1).not.toBe(id2);
  });

  it('should append a toast when a unique fixed ID is provided', () => {
    useUIStore.getState().addToast({ id: 'toast-unique', message: 'Hello', variant: 'success' });

    const { toastQueue } = useUIStore.getState();
    expect(toastQueue).toHaveLength(1);
    expect(toastQueue[0].id).toBe('toast-unique');
    expect(toastQueue[0].message).toBe('Hello');
  });

  it('should REPLACE existing toast when the same ID is reused — no duplicate stacking', () => {
    useUIStore.getState().addToast({ id: 'permission-denied', message: 'First error', variant: 'error' });
    useUIStore.getState().addToast({ id: 'permission-denied', message: 'Replaced error', variant: 'error' });

    const { toastQueue } = useUIStore.getState();
    expect(toastQueue).toHaveLength(1);
    expect(toastQueue[0].id).toBe('permission-denied');
    expect(toastQueue[0].message).toBe('Replaced error');
  });

  it('should replace only the matching toast and leave others untouched', () => {
    useUIStore.getState().addToast({ message: 'Unrelated toast', variant: 'info' });
    useUIStore.getState().addToast({ id: 'permission-denied', message: 'Permission error', variant: 'error' });
    useUIStore.getState().addToast({ id: 'permission-denied', message: 'Updated permission error', variant: 'error' });

    const { toastQueue } = useUIStore.getState();
    expect(toastQueue).toHaveLength(2);

    const permToast = toastQueue.find((t) => t.id === 'permission-denied');
    expect(permToast?.message).toBe('Updated permission error');

    const otherToast = toastQueue.find((t) => t.id !== 'permission-denied');
    expect(otherToast?.message).toBe('Unrelated toast');
  });

  it('should return the provided ID when a fixed ID is passed', () => {
    const returned = useUIStore.getState().addToast({ id: 'my-toast', message: 'Hello', variant: 'success' });
    expect(returned).toBe('my-toast');
  });

  it('should remove a toast by ID', () => {
    useUIStore.getState().addToast({ id: 'remove-me', message: 'I will be removed', variant: 'warning' });
    useUIStore.getState().removeToast('remove-me');

    expect(useUIStore.getState().toastQueue).toHaveLength(0);
  });

  it('should leave queue unchanged when removing a non-existent ID', () => {
    useUIStore.getState().addToast({ message: 'Survivor', variant: 'success' });
    useUIStore.getState().removeToast('does-not-exist');

    expect(useUIStore.getState().toastQueue).toHaveLength(1);
  });
});
