import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 빌드 결과물을 Spring Boot 정적 폴더에 넣는다 → 서버 하나(8080)로 화면과 API를 함께 제공
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
    // 파일 이름에 내용 해시를 붙인다 → 서버가 /assets 를 1년 캐시해도 새 버전은 이름이 달라 바로 받는다.
    // emptyOutDir 로 빌드할 때마다 예전 파일은 지워진다.
    rolldownOptions: {
      output: {
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
      },
    },
  },
  server: {
    // npm run dev(5173) 로 화면을 고칠 때 API 는 Spring Boot(8080)로 넘긴다
    proxy: { '/api': 'http://localhost:8080' },
  },
})
