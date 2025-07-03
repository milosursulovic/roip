export default function setupSignaling(io) {
  io.on("connection", (socket) => {
    console.log("🔌 New client connected:", socket.id);

    socket.on("join", (roomId) => {
      socket.join(roomId);
      socket.to(roomId).emit("user-joined", socket.id);
    });

    socket.on("offer", (data) => {
      socket
        .to(data.target)
        .emit("offer", { from: socket.id, offer: data.offer });
    });

    socket.on("answer", (data) => {
      socket
        .to(data.target)
        .emit("answer", { from: socket.id, answer: data.answer });
    });

    socket.on("ice-candidate", (data) => {
      socket
        .to(data.target)
        .emit("ice-candidate", { from: socket.id, candidate: data.candidate });
    });

    socket.on("disconnect", () => {
      console.log("🔌 Disconnected:", socket.id);
    });
  });
}
