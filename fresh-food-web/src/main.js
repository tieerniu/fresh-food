import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

// 引入全局样式（如果需要）
const app = createApp(App)

const loadStyle = loader => {
    loader().catch(error => {
        console.warn('样式资源加载失败:', error)
    })
}

const useElementPlugins = plugins => {
    plugins.forEach(plugin => {
        if (plugin) app.use(plugin)
    })
}

let traceElementPlusRegistered = false
const registerTraceElementPlus = async () => {
    if (traceElementPlusRegistered) return

    loadStyle(() => import('element-plus/dist/index.css'))
    const { traceElementPlusPlugins } = await import('./plugins/trace-element-plus')
    useElementPlugins(traceElementPlusPlugins)

    traceElementPlusRegistered = true
}

let adminBaseElementPlusRegistered = false
let adminElementPlusRegistered = false
const registerAdminElementPlus = async to => {
    const isDashboardRoute = ['/admin', '/admin/', '/admin/dashboard'].includes(to.path)

    loadStyle(() => import('element-plus/dist/index.css'))
    loadStyle(() => import('./styles/admin-theme.css'))

    if (!adminBaseElementPlusRegistered) {
        const { adminBaseElementPlusPlugins } = await import('./plugins/admin-base-element-plus')
        useElementPlugins(adminBaseElementPlusPlugins)
        adminBaseElementPlusRegistered = true
    }

    if (!isDashboardRoute && !adminElementPlusRegistered) {
        const { adminElementPlusPlugins } = await import('./plugins/admin-element-plus')
        useElementPlugins(adminElementPlusPlugins)
        adminElementPlusRegistered = true
    }
}

router.beforeEach(async (to, from, next) => {
    if (to.path === '/trace') {
        await registerTraceElementPlus()
    } else if (to.path.startsWith('/admin')) {
        await registerAdminElementPlus(to)
    }
    next()
})

app.use(router)
app.mount('#app')
