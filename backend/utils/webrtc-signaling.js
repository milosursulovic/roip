export default function setupSignaling(io) {
  const rooms = {};

  io.on("connection", (socket) => {
    console.log("🔌 New client connected:", socket.id);

    socket.on("join", (roomId) => {
      socket.join(roomId);

      if (!rooms[roomId]) rooms[roomId] = new Set();
      rooms[roomId].add(socket.id);

      io.to(roomId).emit("user-list", Array.from(rooms[roomId]));
      socket.to(roomId).emit("user-joined", socket.id);
    });

    socket.on("offer", (data) => {
      socket.to(data.target).emit("offer", {
        from: socket.id,
        offer: data.offer,
      });
    });

    socket.on("answer", (data) => {
      socket.to(data.target).emit("answer", {
        from: socket.id,
        answer: data.answer,
      });
    });

    socket.on("ice-candidate", (data) => {
      socket.to(data.target).emit("ice-candidate", {
        from: socket.id,
        candidate: data.candidate,
      });
    });

    socket.on("disconnecting", () => {
      for (const roomId of socket.rooms) {
        if (roomId === socket.id) continue;

        if (rooms[roomId]) {
          rooms[roomId].delete(socket.id);
          socket.to(roomId).emit("user-left", socket.id);

          io.to(roomId).emit("user-list", Array.from(rooms[roomId]));

          if (rooms[roomId].size === 0) {
            delete rooms[roomId];
          }
        }
      }
    });

    socket.on("disconnect", () => {
      console.log("🔌 Disconnected:", socket.id);
    });
  });
}
