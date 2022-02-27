package org.team1540.robot2022;

import com.ctre.phoenix.motorcontrol.NeutralMode;
import edu.wpi.first.wpilibj.*;
import edu.wpi.first.wpilibj.XboxController.Button;
import edu.wpi.first.wpilibj.shuffleboard.BuiltInWidgets;
import edu.wpi.first.wpilibj.shuffleboard.Shuffleboard;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.button.JoystickButton;
import edu.wpi.first.wpilibj2.command.button.POVButton;
import edu.wpi.first.wpilibj2.command.button.Trigger;
import org.team1540.robot2022.commands.climber.Climber;
import org.team1540.robot2022.commands.climber.ClimberUpDownCommand;
import org.team1540.robot2022.commands.drivetrain.*;
import org.team1540.robot2022.commands.hood.Hood;
import org.team1540.robot2022.commands.indexer.Indexer;
import org.team1540.robot2022.commands.indexer.IndexerEjectCommand;
import org.team1540.robot2022.commands.intake.Intake;
import org.team1540.robot2022.commands.intake.IntakeSequence;
import org.team1540.robot2022.commands.intake.IntakeSpinCommand;
import org.team1540.robot2022.commands.shooter.ShootSequence;
import org.team1540.robot2022.commands.shooter.Shooter;
import org.team1540.robot2022.utils.ChickenSmartDashboard;
import org.team1540.robot2022.utils.Limelight;
import org.team1540.robot2022.utils.NavX;
import org.team1540.robot2022.utils.RevBlinken;
import org.team1540.robot2022.utils.RevBlinken.GameStage;

public class RobotContainer {
    private final boolean ENABLE_COMPRESSOR = true;

    // Hardware
    public final RevBlinken robotLEDs = new RevBlinken(0);
    public final Limelight limelight = new Limelight("limelight");
    public final NavX navx = new NavX(SPI.Port.kMXP);
    public final PneumaticHub ph = new PneumaticHub(Constants.ph);

    // Subsystems
    public final Drivetrain drivetrain = new Drivetrain(NeutralMode.Brake, navx);
    public final Hood hood = new Hood();
    public final Intake intake = new Intake();
    public final Indexer indexer = new Indexer(NeutralMode.Brake);
    public final Shooter shooter = new Shooter();
    public final Climber climber = new Climber();

    // Controllers
    public final XboxController driverController = new XboxController(0);
    public final XboxController copilotController = new XboxController(1);

    // Buttons
    public final DigitalInput zeroOdometry = new DigitalInput(0);

    // Commands
    public final IndexerEjectCommand indexerEjectCommand = new IndexerEjectCommand(indexer, intake);
    public final IntakeSequence intakeSequence = new IntakeSequence(intake, indexer);
    public final ShootSequence shootSequence = new ShootSequence(shooter, indexer, drivetrain, hood, intake, limelight);
    public final ClimberUpDownCommand climberUpDownCommand = new ClimberUpDownCommand(climber, copilotController);

    // coop:button(LJoystick,Left tank,pilot)
    // coop:button(RJoystick,Right tank,pilot)
    // coop:button(LTrigger,Forward,pilot)
    // coop:button(RTrigger,Reverse,pilot)
    public final TankDriveCommand tankDriveCommand = new TankDriveCommand(drivetrain, driverController);

    // Misc
    private final SendableChooser<Command> autoChooser = new SendableChooser<>();

    public RobotContainer() {
        initSmartDashboard();
        configureButtonBindings();
        initModeTransitionBindings();
        DriverStation.silenceJoystickConnectionWarning(false);

        if (ENABLE_COMPRESSOR) {
            ph.enableCompressorDigital();
        } else {
            ph.disableCompressor();
        }
    }

    private void configureButtonBindings() {
        // Driver

        // coop:button(RBumper,Point to target [hold],pilot)
        new JoystickButton(driverController, Button.kRightBumper.value)
                .whenHeld(new PointToTarget(drivetrain, limelight));

        // coop:button(LBumper,Shoot [hold],pilot)
        new JoystickButton(driverController, Button.kLeftBumper.value)
                .whenHeld(shootSequence);

        // coop:button(DPadUp,Shoot from HUB [press],pilot)
        new POVButton(driverController, 0) // D-pad up
                .whenPressed(new InstantCommand(() -> shootSequence.shootFromHub = true));
        // coop:button(DPadDown,Shoot from not hub [press],pilot)
        new POVButton(driverController, 180) // D-pad down
                .whenPressed(new InstantCommand(() -> shootSequence.shootFromHub = false));

        // Intake/indexer

        // coop:button(X,Start intake and indexer [press],pilot)
        new JoystickButton(driverController, Button.kX.value)
                .cancelWhenPressed(indexerEjectCommand)
                .whenPressed(intakeSequence);
        // coop:button(Y,Eject intake and indexer [press],pilot)
        new JoystickButton(driverController, Button.kY.value)
                .whenPressed(indexerEjectCommand)
                .cancelWhenPressed(intakeSequence);
        // coop:button(A,Stop indexer and intake [press],pilot)
        new JoystickButton(driverController, Button.kA.value)
                .cancelWhenPressed(indexerEjectCommand)
                .cancelWhenPressed(intakeSequence);

        // coop:button(B,Toggle intake fold [press],pilot)
        new JoystickButton(driverController, Button.kB.value)
                .whenPressed(new InstantCommand(() -> intake.setFold(!intake.getFold())));

        // Copilot

        // coop:button(B,Toggle intake fold [press],copilot)
        new JoystickButton(copilotController, Button.kB.value)
                .whenPressed(new InstantCommand(() -> intake.setFold(!intake.getFold())));

        // coop:button(X,Spin up shooter [press],copilot)
        new JoystickButton(copilotController, Button.kX.value)
                .and(new Trigger(() -> !shootSequence.isScheduled()))
                .whenActive(shooter.commandSetVelocity(InterpolationTable.copilotSpinupFront, InterpolationTable.copilotSpinupRear));
        // coop:button(Y,Stop shooter spinup shooter [press],copilot)
        new JoystickButton(copilotController, Button.kY.value)
                .and(new Trigger(() -> !shootSequence.isScheduled()))
                .whenActive(shooter.commandStop());

        // coop:button(LBumper,Manual intake [hold],copilot)
        new JoystickButton(copilotController, Button.kLeftBumper.value)
                .whileHeld(new IntakeSpinCommand(intake, indexer, Constants.IntakeConstants.speed));
        // coop:button(RBumper,Manual reverse intake [hold],copilot)
        new JoystickButton(copilotController, Button.kRightBumper.value)
                .whileHeld(new IntakeSpinCommand(intake, -Constants.IntakeConstants.speed));

        // coop:button(DPadRight,Run intake and indexer [press],copilot)
        new POVButton(copilotController, 90) // D-pad right
                .cancelWhenPressed(indexerEjectCommand)
                .whenPressed(intakeSequence);
        // coop:button(DPadDown,Stop indexer and intake [press],copilot)
        new POVButton(copilotController, 180) // D-pad down
                .cancelWhenPressed(indexerEjectCommand)
                .cancelWhenPressed(intakeSequence);
        // coop:button(DPadLeft,Eject balls from intake,copilot)
        new POVButton(copilotController, 270) // D-pad left
                .whenPressed(new InstantCommand(() -> climber.setSolenoids(false)));
        // coop:button(DPadUp,Toggle climber solenoids [press],copilot)
        new POVButton(copilotController, 0)
                .whenPressed(new InstantCommand(() -> climber.setSolenoids(true)));
        // Robot hardware button
        new Trigger(zeroOdometry::get)
                .whenActive(new OdometryResetSequence(drivetrain, navx, limelight));

        // SmartDashboard
        SmartDashboard.putData("ph/disableCompressor", new InstantCommand(ph::disableCompressor));
    }

    private void initModeTransitionBindings() {
        var autonomous = new Trigger(DriverStation::isAutonomousEnabled);
        var teleop = new Trigger(DriverStation::isTeleopEnabled);
        var disabled = new Trigger(DriverStation::isDisabled);

        teleop.whenActive(() -> robotLEDs.applyPattern(DriverStation.getAlliance(), GameStage.TELEOP));
        autonomous.whenActive(() -> robotLEDs.applyPattern(DriverStation.getAlliance(), GameStage.AUTONOMOUS));
        disabled.whenActive(() -> robotLEDs.applyPattern(DriverStation.getAlliance(), GameStage.DISABLE));
    }

    private void initSmartDashboard() {
        // autoChooser.addOption("1 Ball", new ShootSequence(shooter, indexer, drivetrain, hood, intake, limelight));
        autoChooser.setDefaultOption("2 Ball A", new Auto2BallSequence(drivetrain, intake, indexer, shooter, hood, limelight, true));
        autoChooser.addOption("2 Ball B", new Auto2BallSequence(drivetrain, intake, indexer, shooter, hood, limelight, false));
        autoChooser.addOption("3 Ball", new Auto3BallSequence(drivetrain, intake, indexer, shooter, hood, limelight));
        autoChooser.addOption("4 Ball", new Auto4BallSequence(drivetrain, intake, indexer, shooter, hood, limelight));

        SmartDashboard.putData(autoChooser);
        SmartDashboard.putData(CommandScheduler.getInstance());

        Shuffleboard.getTab("SmartDashboard")
                .add("NavX", navx)
                .withWidget(BuiltInWidgets.kGyro);

        // Indexer values
        ChickenSmartDashboard.putDefaultNumber("intake/speed", 0.5);
        ChickenSmartDashboard.putDefaultNumber("indexer/waitDuration/top", 0.2);
        ChickenSmartDashboard.putDefaultNumber("indexer/waitDuration/bottom", 0.2);

        // PointToTarget values
        ChickenSmartDashboard.putDefaultNumber("pointToTarget/kP", 0.7);
        ChickenSmartDashboard.putDefaultNumber("pointToTarget/kD", 0.4);

        ChickenSmartDashboard.putDefaultNumber("pointToTarget/pidClamp", 0.8);
        ChickenSmartDashboard.putDefaultNumber("pointToTarget/targetDeadzoneDegrees", 2);

        SmartDashboard.putNumber("pointToTarget/pidOutput", 0);
        SmartDashboard.putNumber("pointToTarget/degreeDistanceToTarget", 0);

        SmartDashboard.putBoolean("pointToTarget/isClamping", false);

        // Drivetrain values
        ChickenSmartDashboard.putDefaultNumber("ramsetePID/kP", 0.5);
        ChickenSmartDashboard.putDefaultNumber("drivetrain/tankDrive/maxVelocity", 1);
        ChickenSmartDashboard.putDefaultNumber("drivetrain/tankDrive/maxAcceleration", 0.5);

        // Climber values
        ChickenSmartDashboard.putDefaultNumber("climber/PID/kP", 0.3);

        SmartDashboard.putNumber("shooter/tuning/frontRPM", -1000);
        SmartDashboard.putNumber("shooter/tuning/rearRPM", -1000);

        // Shoot when we're within this RPM from the target velocity (sum of both flywheel errors, plus or minus)
        SmartDashboard.putNumber("shooter/tuning/targetError", 30);
    }

    public Command getAutonomousCommand() {
        return autoChooser.getSelected();
    }
}
