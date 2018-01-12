from command import Command
from repository import Repository
from utils import which
import os


class TeamwareRepository(Repository):
    def __init__(self, logger, path, project, command, env, hooks):

        super().__init__(logger, path, project, command, env, hooks)

        #
        # Teamware is different than the rest of the repositories.
        # It really needs a path to the tools since the 'bringover'
        # binary by itself is not sufficient to update the workspace.
        # So instead of passing path to the binary, the command
        # argument contains the path to the directory that contains
        # the binaries.
        #
        if command:
            try:
                path = os.environ['PATH']
            except:
                self.logger.error("Cannot get PATH env var")
                raise OSError

            path += ":" + command
            self.env['PATH'] = path
        else:
            self.logger.error("Cannot get path to Teamware commands")
            raise OSError

    def reposync(self):
        #
        # If there is no Teamware specific subdirectory, do not bother
        # syncing.
        #
        if not os.path.isdir(os.path.join(self.path, "Codemgr_wsdata")):
            self.logger.debug("Not a teamware repository: {} -> not syncing".
                              format(self.path))
            return 0

        hg_command = ["bringover"]
        cmd = Command(hg_command, work_dir=self.path, env_vars=self.env)
        cmd.execute()
        self.logger.info(cmd.getoutputstr())
        if cmd.getretcode() != 0 or cmd.getstate() != Command.FINISHED:
            self.logger.error("failed to perform bringover for {}".
                              format(self.path))
            return 1

        return 0
