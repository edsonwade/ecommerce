const CATEGORY_IMAGES: Record<string, string> = {
  keyboards: 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=400&auto=format&fit=crop',
  mice: 'https://images.unsplash.com/photo-1527814050087-3793815479db?w=400&auto=format&fit=crop',
  monitors: 'https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?w=400&auto=format&fit=crop',
  audio: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=400&auto=format&fit=crop',
  videostreaming: 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?w=400&auto=format&fit=crop',
  connectivity: 'https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?w=400&auto=format&fit=crop',
  storage: 'https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?w=400&auto=format&fit=crop',
  desksetup: 'https://images.unsplash.com/photo-1593642632559-0c6d3fc62b89?w=400&auto=format&fit=crop',
  gaminggear: 'https://images.unsplash.com/photo-1542567455-cd733f23fbb1?w=400&auto=format&fit=crop',
  gaming: 'https://images.unsplash.com/photo-1542567455-cd733f23fbb1?w=400&auto=format&fit=crop',
  officeequipment: 'https://images.unsplash.com/photo-1517430816045-df4b7de11d1d?w=400&auto=format&fit=crop',
  networking: 'https://images.unsplash.com/photo-1544197150-b99a580bb7a8?w=400&auto=format&fit=crop',
  pccomponents: 'https://images.unsplash.com/photo-1591488320449-011701bb6704?w=400&auto=format&fit=crop',
  power: 'https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?w=400&auto=format&fit=crop',
  smarttech: 'https://images.unsplash.com/photo-1546868871-7041f2a55e12?w=400&auto=format&fit=crop',
  portability: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=400&auto=format&fit=crop',
  printing: 'https://images.unsplash.com/photo-1578662996442-48f60103fc96?w=400&auto=format&fit=crop',
  accessories: 'https://images.unsplash.com/photo-1625723044792-44de16ccb4e9?w=400&auto=format&fit=crop',
  default: 'https://images.unsplash.com/photo-1518770660439-4636190af475?w=400&auto=format&fit=crop',
};

export function getCategoryFallbackImage(categoryName?: string | null): string {
  if (!categoryName) return CATEGORY_IMAGES.default;
  // Normalise: lowercase, strip spaces and special characters
  const key = categoryName.toLowerCase().replace(/[^a-z]/g, '');
  return CATEGORY_IMAGES[key] ?? CATEGORY_IMAGES.default;
}
