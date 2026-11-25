module.exports = {
  apps: [
    {
      name: 'healthdekho-api',
      cwd: '/home/user/webapp/backend',
      script: '/usr/bin/mvn',
      args: '-q spring-boot:run',
      interpreter: 'none',
      env: { PORT: '8080', SPRING_PROFILES_ACTIVE: 'local', AI_ENABLED: 'false', AWS_REGION: 'us-east-1', JAVA_TOOL_OPTIONS: '-Xmx320m -XX:MaxMetaspaceSize=160m' },
      watch: false,
      instances: 1,
      exec_mode: 'fork',
    },
    {
      name: 'healthdekho-web',
      cwd: '/home/user/webapp/frontend',
      script: 'npm',
      args: 'run dev -- --host 0.0.0.0 --port 3000',
      env: { API_PROXY_TARGET: 'http://127.0.0.1:8080' },
      watch: false,
      instances: 1,
      exec_mode: 'fork',
    },
  ],
}
