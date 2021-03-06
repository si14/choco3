/**
 * Copyright (c) 2014,
 *       Charles Prud'homme (TASC, INRIA Rennes, LINA CNRS UMR 6241),
 *       Jean-Guillaume Fages (COSLING S.A.S.).
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.chocosolver.solver.variables.delta.monitor;

import org.chocosolver.solver.Cause;
import org.chocosolver.solver.ICause;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.search.loop.TimeStampedObject;
import org.chocosolver.solver.variables.delta.IIntDeltaMonitor;
import org.chocosolver.solver.variables.delta.IIntervalDelta;
import org.chocosolver.util.procedure.IntProcedure;
import org.chocosolver.util.procedure.SafeIntProcedure;

/**
 * <br/>
 *
 * @author Charles Prud'homme
 * @since 07/12/11
 */
public class IntervalDeltaMonitor extends TimeStampedObject implements IIntDeltaMonitor {

    protected final IIntervalDelta delta;
    protected int first, last, frozenFirst, frozenLast;
    protected ICause propagator;

    public IntervalDeltaMonitor(IIntervalDelta delta, ICause propagator) {
		super(delta.getSearchLoop());
        this.delta = delta;
        this.first = 0;
        this.last = 0;
        this.frozenFirst = 0;
        this.frozenLast = 0;
        this.propagator = propagator;
    }

    @Override
    public void freeze() {
		if (needReset()) {
			this.first = this.last = 0;
			resetStamp();
		}
        this.frozenFirst = first; // freeze indices
        this.frozenLast = last = delta.size();
    }

    @Override
    public void unfreeze() {
        //propagator is idempotent
        delta.lazyClear();    // fix 27/07/12
        resetStamp();
        this.first = this.last = delta.size();
    }

    @Override
    public void forEachRemVal(SafeIntProcedure proc) {
		for (int i = frozenFirst; i < frozenLast; i++) {
			if (propagator == Cause.Null || propagator != delta.getCause(i)) {
				int lb = delta.getLB(i);
				int ub = delta.getUB(i);
				for (; lb <= ub; lb++) {
					proc.execute(lb);
				}
			}
        }
    }

    @Override
    public void forEachRemVal(IntProcedure proc) throws ContradictionException {
		for (int i = frozenFirst; i < frozenLast; i++) {
			if (propagator == Cause.Null || propagator != delta.getCause(i)) {
				int lb = delta.getLB(i);
				int ub = delta.getUB(i);
				for (; lb <= ub; lb++) {
					proc.execute(lb);
				}
			}
		}
    }

    @Override
    public String toString() {
        return String.format("(%d,%d) => (%d,%d) :: %d", first, last, frozenFirst, frozenLast, delta.size());
    }
}
