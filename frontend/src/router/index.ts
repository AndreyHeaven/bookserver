import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
  },
  {
    path: '/p/lists/:token',
    name: 'public-list',
    component: () => import('@/views/PublicListView.vue'),
  },
  {
    path: '/',
    component: () => import('@/components/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/books' },
      { path: 'books', name: 'books', component: () => import('@/views/BooksView.vue') },
      {
        path: 'books/:id',
        name: 'book-details',
        component: () => import('@/views/BookDetailsView.vue'),
      },
      { path: 'authors', name: 'authors', component: () => import('@/views/AuthorsView.vue') },
      {
        path: 'authors/:id',
        name: 'author-details',
        component: () => import('@/views/AuthorDetailsView.vue'),
      },
      { path: 'genres', name: 'genres', component: () => import('@/views/GenresView.vue') },
      {
        path: 'genres/:id',
        name: 'genre-details',
        component: () => import('@/views/GenreDetailsView.vue'),
      },
      { path: 'lists', name: 'my-lists', component: () => import('@/views/MyListsView.vue') },
      {
        path: 'lists/:id',
        name: 'list-details',
        component: () => import('@/views/ListDetailsView.vue'),
      },
      {
        path: 'imports', name: 'imports', component: () => import('@/views/ImportsView.vue'), meta: { requiresAdmin: true },
      },
      {
        path: 'admin', name: 'admin', component: () => import('@/views/AdminView.vue'), meta: { requiresAdmin: true },
      },
      {
        path: 'conversions',
        name: 'conversions',
        component: () => import('@/views/ConversionsView.vue'),
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/books' },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'books' }
  }
  return true
})

export default router
