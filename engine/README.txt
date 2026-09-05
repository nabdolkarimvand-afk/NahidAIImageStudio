The app bundles an arm64 Android sd-cli executable at build time.
The GitHub Actions workflow downloads stable-diffusion.cpp, builds sd-cli for Android arm64, and copies it into app/src/main/assets/engine/arm64-v8a/.
