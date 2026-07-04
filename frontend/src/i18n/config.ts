import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

// Supported languages, surfaced by the navbar LanguageSwitcher.
export const SUPPORTED_LANGUAGES = [
  { code: 'en', label: 'English', flag: '🇬🇧' },
  { code: 'fr', label: 'Français', flag: '🇫🇷' },
  { code: 'pt', label: 'Português', flag: '🇵🇹' },
  { code: 'es', label: 'Español', flag: '🇪🇸' },
] as const;

export type LanguageCode = (typeof SUPPORTED_LANGUAGES)[number]['code'];

// Translation resources. Navigation + common surfaces are covered first;
// remaining pages fall back to English (fallbackLng) until translated.
const resources = {
  en: {
    translation: {
      nav: {
        catalog: 'Catalog',
        myOrders: 'My Orders',
        settings: 'Settings',
        signIn: 'Sign in',
        signOut: 'Sign out',
        account: 'My Account',
        sellerHub: 'Seller Hub',
        admin: 'Admin',
        cart: 'Cart',
        themeLight: 'Light mode',
        themeDark: 'Dark mode',
        language: 'Language',
      },
      toast: { signedOut: 'Signed out successfully' },
      catalog: {
        eyebrow: 'ALL PRODUCTS',
        title: 'Catalog',
        productsCount: '{{count}} products',
        searchLabel: 'Search products',
        category: 'Category',
        allCategories: 'All categories',
        sortBy: 'Sort by',
        clearFilters: 'Clear filters',
        noResults: 'No products found',
      },
    },
  },
  fr: {
    translation: {
      nav: {
        catalog: 'Catalogue',
        myOrders: 'Mes commandes',
        settings: 'Paramètres',
        signIn: 'Se connecter',
        signOut: 'Se déconnecter',
        account: 'Mon compte',
        sellerHub: 'Espace vendeur',
        admin: 'Admin',
        cart: 'Panier',
        themeLight: 'Mode clair',
        themeDark: 'Mode sombre',
        language: 'Langue',
      },
      toast: { signedOut: 'Déconnexion réussie' },
      catalog: {
        eyebrow: 'TOUS LES PRODUITS',
        title: 'Catalogue',
        productsCount: '{{count}} produits',
        searchLabel: 'Rechercher des produits',
        category: 'Catégorie',
        allCategories: 'Toutes les catégories',
        sortBy: 'Trier par',
        clearFilters: 'Effacer les filtres',
        noResults: 'Aucun produit trouvé',
      },
    },
  },
  pt: {
    translation: {
      nav: {
        catalog: 'Catálogo',
        myOrders: 'Minhas encomendas',
        settings: 'Definições',
        signIn: 'Entrar',
        signOut: 'Terminar sessão',
        account: 'Minha conta',
        sellerHub: 'Painel do vendedor',
        admin: 'Admin',
        cart: 'Carrinho',
        themeLight: 'Modo claro',
        themeDark: 'Modo escuro',
        language: 'Idioma',
      },
      toast: { signedOut: 'Sessão terminada com sucesso' },
      catalog: {
        eyebrow: 'TODOS OS PRODUTOS',
        title: 'Catálogo',
        productsCount: '{{count}} produtos',
        searchLabel: 'Pesquisar produtos',
        category: 'Categoria',
        allCategories: 'Todas as categorias',
        sortBy: 'Ordenar por',
        clearFilters: 'Limpar filtros',
        noResults: 'Nenhum produto encontrado',
      },
    },
  },
  es: {
    translation: {
      nav: {
        catalog: 'Catálogo',
        myOrders: 'Mis pedidos',
        settings: 'Configuración',
        signIn: 'Iniciar sesión',
        signOut: 'Cerrar sesión',
        account: 'Mi cuenta',
        sellerHub: 'Panel de vendedor',
        admin: 'Admin',
        cart: 'Carrito',
        themeLight: 'Modo claro',
        themeDark: 'Modo oscuro',
        language: 'Idioma',
      },
      toast: { signedOut: 'Sesión cerrada correctamente' },
      catalog: {
        eyebrow: 'TODOS LOS PRODUCTOS',
        title: 'Catálogo',
        productsCount: '{{count}} productos',
        searchLabel: 'Buscar productos',
        category: 'Categoría',
        allCategories: 'Todas las categorías',
        sortBy: 'Ordenar por',
        clearFilters: 'Borrar filtros',
        noResults: 'No se encontraron productos',
      },
    },
  },
} as const;

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'en',
    supportedLngs: SUPPORTED_LANGUAGES.map((l) => l.code),
    interpolation: { escapeValue: false },
    detection: {
      // Persist the user's choice; honour it on the next visit.
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'obsidian-lang',
      caches: ['localStorage'],
    },
  });

export default i18n;
