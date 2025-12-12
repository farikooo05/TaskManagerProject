import { useEffect, useState } from "react";
import { DragDropContext, Droppable, Draggable } from "@hello-pangea/dnd";
import { useAuth } from "../context/AuthContext";
import { getTasks, changeTaskStatus } from "../api/api";
import Card from "../components/Card";
import { StatusBadge } from "../components/StatusBadge";
import { PriorityBadge } from "../components/PriorityBadge";

const STATUS_ORDER = ["CREATED", "IN_PROGRESS", "RESOLVED", "DONE"];
const STATUS_LABELS = {
  CREATED: "Created",
  IN_PROGRESS: "In progress",
  RESOLVED: "Resolved",
  DONE: "Done",
};

export default function TeamBoard() {
  const { user } = useAuth();
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [updating, setUpdating] = useState(false);

  const isHeadManager = user?.role === "HEAD_MANAGER";
  const isHrManager = user?.role === "HR_MANAGER";

  useEffect(() => {
    loadTasks();
  }, []);

  const loadTasks = () => {
    setLoading(true);
    getTasks()
      .then((res) => setTasks(res.data))
      .finally(() => setLoading(false));
  };

  const onDragEnd = async (result) => {
    if (!isHeadManager) return; // HR_MANAGER is read-only on team board

    const { source, destination, draggableId } = result;
    if (!destination) return;

    const sourceStatus = source.droppableId;
    const destStatus = destination.droppableId;
    if (sourceStatus === destStatus) return;

    const taskId = Number(draggableId);

    // Head Manager can move anywhere including DONE
    setTasks((prev) =>
      prev.map((t) => (t.id === taskId ? { ...t, status: destStatus } : t))
    );

    try {
      setUpdating(true);
      await changeTaskStatus({ taskId, status: destStatus });
    } catch (err) {
      console.error(err);
      loadTasks();
    } finally {
      setUpdating(false);
    }
  };

  if (!user) return null;

  if (loading) {
    return (
      <div className="p-6 text-center text-slate-300">
        Loading team board...
      </div>
    );
  }

  const tasksByStatus = (status) =>
    tasks.filter((t) => t.status === status || (!t.status && status === "CREATED"));

  const BoardColumns = () => (
    <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
      {STATUS_ORDER.map((status) => (
        <Droppable key={status} droppableId={status} isDropDisabled={!isHeadManager}>
          {(provided) => (
            <div
              ref={provided.innerRef}
              {...provided.droppableProps}
              className="bg-slate-900/70 border border-slate-800 rounded-2xl p-3 min-h-[200px] flex flex-col"
            >
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-semibold text-slate-100">
                  {STATUS_LABELS[status]}
                </span>
                <span className="text-[11px] text-slate-500">
                  {tasksByStatus(status).length}
                </span>
              </div>

              <div className="space-y-2 flex-1">
                {tasksByStatus(status).map((task, index) => (
                  <Draggable
                    key={task.id}
                    draggableId={String(task.id)}
                    index={index}
                    isDragDisabled={!isHeadManager}
                  >
                    {(providedDrag) => (
                      <div
                        ref={providedDrag.innerRef}
                        {...providedDrag.draggableProps}
                        {...providedDrag.dragHandleProps}
                      >
                        <Card
                          title={task.title || `Task #${task.id}`}
                          footer={
                          <div className="flex flex-col gap-1">

                            {/* Status and Priority row */}
                            <div className="flex items-center justify-between w-full mb-2">
                              <StatusBadge status={task.status} />
                              <PriorityBadge priority={task.priority} />
                            </div>


                            {/* Assigned user row */}
                            {task.employee && (
                              <div className="flex items-center gap-2 p-2  rounded-md bg-slate-800/40">

                                {/* Avatar */}
                                <div className="w-6 h-6 rounded-full bg-blue-600 text-white text-[10px] flex items-center justify-center font-bold shrink-0">
                                  {task.employee.name[0]}
                                  {task.employee.surname[0]}
                                </div>

                                {/* Name + Email */}
                                <div className="min-w-0"> {/* allows wrapping + prevents overflow */}
                                  <div className="text-[11px] text-slate-200 font-medium truncate">
                                    {task.employee.name} {task.employee.surname}
                                  </div>
                                  <div className="text-[10px] text-slate-400 truncate">
                                    {task.employee.email}
                                  </div>
                                </div>

                              </div>
                            )}
                          </div>
                        }
                        >
                          <p className="text-xs text-slate-300 mb-2 break-words whitespace-pre-wrap max-h-16 overflow-hidden">
                            {task.description}
                          </p>
                        </Card>
                      </div>
                    )}
                  </Draggable>
                ))}
                {provided.placeholder}
                {tasksByStatus(status).length === 0 && (
                  <p className="text-[11px] text-slate-500 text-center mt-4">
                    No tasks here.
                  </p>
                )}
              </div>
            </div>
          )}
        </Droppable>
      ))}
    </div>
  );

  return (
    <div className="max-w-6xl mx-auto px-4 py-6">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-50">Team Board</h1>
          <p className="text-xs text-slate-400">
            {isHeadManager
              ? "Drag tasks to manage the whole team workflow."
              : "View all team tasks and their current status."}
          </p>
        </div>
        {updating && isHeadManager && (
          <div className="text-[11px] text-slate-400">
            Updating status...
          </div>
        )}
      </div>

      {/* DragDropContext only matters for Head Manager */}
      <DragDropContext onDragEnd={onDragEnd}>
        <BoardColumns />
      </DragDropContext>
    </div>
  );
}
