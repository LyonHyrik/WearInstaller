p = 'app/src/main/java/com/lyon/wearinstaller/MainActivity.kt'
s = open(p, encoding='utf-8').read()

old_guard = "            // only function key triggers install, avoid intercepting crown etc\n            if (event.keyCode == 0x02FE) {\n                val container = (application as WearInstallerApp).container\n                val state = container.coordinator.state.value\n                if (state.pending != null && state.job is com.lyon.wearinstaller.data.JobState.Idle) {\n                    container.coordinator.startInstall()\n                    return true\n                }\n            }"
# The actual comment may differ; try matching the inner block instead
old_inner = "                val container = (application as WearInstallerApp).container\n                val state = container.coordinator.state.value\n                if (state.pending != null && state.job is com.lyon.wearinstaller.data.JobState.Idle) {\n                    container.coordinator.startInstall()\n                    return true\n                }\n            }"
new_inner = "                val container = (application as WearInstallerApp).container\n                val state = container.coordinator.state.value\n                if (state.pending != null && state.job is com.lyon.wearinstaller.data.JobState.Idle) {\n                    container.coordinator.startInstall()\n                    return true\n                }"
assert old_inner in s, 'inner not found'
s = s.replace(old_inner, new_inner, 1)

# Add onGenericMotionEvent before final closing brace
marker = "\n}\n"
idx = s.rfind(marker)
assert idx != -1
addition = "\n    override fun onGenericMotionEvent(event: android.view.MotionEvent?): Boolean {\n        android.util.Log.d(\"WearInstaller\", \"onGenericMotionEvent action=\" + (event?.action))\n        return super.onGenericMotionEvent(event)\n    }\n"
s = s[:idx] + addition + s[idx:]

open(p, 'w', encoding='utf-8').write(s)
print('done')
"