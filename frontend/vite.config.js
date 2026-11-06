import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 빌드 결과물을 Spring Boot 정적 폴더에 넣는다 → 서버 하나(8080)로 화면과 API를 함께 제공
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
    // 파일 이름을 고정해서 다시 빌드해도 같은 파일을 덮어쓰게 한다(오래된 파일이 쌓이지 않도록)
    rolldownOptions: {
      output: {
        entryFileNames: 'assets/[name].js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name][extname]',
      },
    },
  },
  server: {
    // npm run dev(5173) 로 화면을 고칠 때 API 는 Spring Boot(8080)로 넘긴다
    proxy: { '/api': 'http://localhost:8080' },
  },
})
