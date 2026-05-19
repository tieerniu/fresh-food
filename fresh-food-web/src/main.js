import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

// 引入全局样式（如果需要）
const app = createApp(App)

let traceElementPlusRegistered = false
const registerTraceElementPlus = async () => {
    if (traceElementPlusRegistered) return

    await import('element-plus/dist/index.css')
    const {
        ElAlert,
        ElButton,
        ElIcon,
        ElImage,
        ElInput,
        ElTag,
        ElTimeline,
        ElTimelineItem
    } = await import('element-plus')

    ;[
        ElAlert,
        ElButton,
        ElIcon,
        ElImage,
        ElInput,
        ElTag,
        ElTimeline,
        ElTimelineItem
    ].forEach(plugin => app.use(plugin))

    traceElementPlusRegistered = true
}

let adminElementPlusRegistered = false
const registerAdminElementPlus = async () => {
    if (adminElementPlusRegistered) return

    await import('element-plus/dist/index.css')
    await import('./styles/admin-theme.css')
    const {
        ElAside,
        ElButton,
        ElCard,
        ElCheckbox,
        ElCol,
        ElContainer,
        ElDatePicker,
        ElDescriptions,
        ElDescriptionsItem,
        ElDialog,
        ElDrawer,
        ElDropdown,
        ElDropdownItem,
        ElDropdownMenu,
        ElEmpty,
        ElForm,
        ElFormItem,
        ElHeader,
        ElIcon,
        ElInput,
        ElInputNumber,
        ElLink,
        ElLoading,
        ElMain,
        ElMenu,
        ElMenuItem,
        ElOption,
        ElPagination,
        ElPopover,
        ElProgress,
        ElRadioButton,
        ElRadioGroup,
        ElRow,
        ElSelect,
        ElSwitch,
        ElTable,
        ElTableColumn,
        ElTag,
        ElText,
        ElTooltip
    } = await import('element-plus')

    ;[
        ElAside,
        ElButton,
        ElCard,
        ElCheckbox,
        ElCol,
        ElContainer,
        ElDatePicker,
        ElDescriptions,
        ElDescriptionsItem,
        ElDialog,
        ElDrawer,
        ElDropdown,
        ElDropdownItem,
        ElDropdownMenu,
        ElEmpty,
        ElForm,
        ElFormItem,
        ElHeader,
        ElIcon,
        ElInput,
        ElInputNumber,
        ElLink,
        ElLoading,
        ElMain,
        ElMenu,
        ElMenuItem,
        ElOption,
        ElPagination,
        ElPopover,
        ElProgress,
        ElRadioButton,
        ElRadioGroup,
        ElRow,
        ElSelect,
        ElSwitch,
        ElTable,
        ElTableColumn,
        ElTag,
        ElText,
        ElTooltip
    ].forEach(plugin => app.use(plugin))

    adminElementPlusRegistered = true
}

router.beforeEach(async (to, from, next) => {
    if (to.path === '/trace') {
        await registerTraceElementPlus()
    } else if (to.path.startsWith('/admin')) {
        await registerAdminElementPlus()
    }
    next()
})

app.use(router)
app.mount('#app')
