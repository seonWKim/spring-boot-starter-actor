# Chat Application Frontend

Modern React frontend for the Spring Boot Actor Chat application, built with Vite, TypeScript, and Tailwind CSS.

## Tech Stack

- **React 19** - UI framework
- **TypeScript** - Type safety
- **Vite** - Fast build tool and dev server
- **Tailwind CSS** - Utility-first CSS framework
- **shadcn/ui** - Beautiful, accessible components
- **Lucide React** - Icon library

## Features

- 🌐 **Multi-Node Visualization** - Connect to multiple cluster nodes simultaneously
- 💬 Real-time WebSocket communication
- 📊 Side-by-side chat panels showing distributed messaging
- 👥 Online user presence tracking per node
- 🔄 Connection status indicator for each node
- 📱 Responsive, accessible design
- 🎯 Perfect for demonstrating actor-based distributed systems

## Development

### Prerequisites

- Node.js 18+ and npm
- Running Spring Boot backend on port 8080

### Setup

\`\`\`bash
# Install dependencies
npm install

# Start development server
npm run dev
\`\`\`

The dev server will start on \`http://localhost:5173\` with automatic WebSocket proxying to the backend.

## Building for Production

\`\`\`bash
# Build for production
npm run build
\`\`\`

This outputs to \`../src/main/resources/static\` and is automatically served by Spring Boot.

## Project Structure

\`\`\`
frontend/
├── src/
│   ├── components/         # React components
│   ├── hooks/             # Custom React hooks
│   ├── services/          # Business logic
│   ├── lib/               # Utilities
│   └── App.tsx            # Main application
├── vite.config.ts         # Vite configuration
└── tailwind.config.js     # Tailwind configuration
\`\`\`
