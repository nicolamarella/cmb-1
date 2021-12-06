package core;

import java.util.List;

import movement.MovementModel;
import movement.schedule.Student;
import routing.MessageRouter;

public class DTNHostStudent extends DTNHost {
    private Student student;

    public DTNHostStudent(List<MessageListener> msgLs, List<MovementListener> movLs, String groupId,
            List<NetworkInterface> interf, ModuleCommunicationBus comBus, MovementModel mmProto,
            MessageRouter mRouterProto) {
        super(msgLs, movLs, groupId, interf, comBus, mmProto, mRouterProto);
        // TODO Auto-generated constructor stub
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

}
